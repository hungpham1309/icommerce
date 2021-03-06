package vn.icommerce.iam.app.buyertoken;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.icommerce.iam.app.buyer.BuyerAppService;
import vn.icommerce.iam.app.buyer.CreateBuyerCmd;
import vn.icommerce.iam.app.component.Encoder;
import vn.icommerce.iam.app.component.SocialGateway;
import vn.icommerce.iam.app.component.TokenCryptoEngine;
import vn.icommerce.sharedkernel.app.component.TxManager;
import vn.icommerce.sharedkernel.domain.exception.DomainException;
import vn.icommerce.sharedkernel.domain.model.Buyer;
import vn.icommerce.sharedkernel.domain.model.BuyerToken;
import vn.icommerce.sharedkernel.domain.model.DomainCode;
import vn.icommerce.sharedkernel.domain.model.SocialPlatform;
import vn.icommerce.sharedkernel.domain.repository.BuyerRepository;

/**
 * Standard implementation for the operator service.
 */
@Service
@Slf4j
public class StdBuyerTokenAppService implements BuyerTokenAppService {

  private final BuyerRepository buyerRepository;

  private final BuyerAppService buyerAppService;

  private final SocialGateway socialGateway;

  private final TxManager txManager;

  private final TokenCryptoEngine tokenCryptoEngine;

  private final long tokenExpirationInS;

  private final Encoder encoder;

  /**
   * Constructor to inject dependencies.
   */
  StdBuyerTokenAppService(
      @Value("#{tokenConfig.expirationInS}") long tokenExpirationInS,
      BuyerRepository buyerRepository,
      BuyerAppService buyerAppService,
      TokenCryptoEngine tokenCryptoEngine,
      TxManager txManager,
      SocialGateway socialGateway, Encoder encoder) {
    this.buyerAppService = buyerAppService;
    this.tokenExpirationInS = tokenExpirationInS;
    this.buyerRepository = buyerRepository;
    this.tokenCryptoEngine = tokenCryptoEngine;
    this.txManager = txManager;
    this.socialGateway = socialGateway;
    this.encoder = encoder;
  }

  private String createToken(Long buyerId) {
    var buyerToken = new BuyerToken()
        .setBuyerId(buyerId)
        .setExpirationDuration(tokenExpirationInS);

    return tokenCryptoEngine.signBuyerToken(buyerToken);
  }

  @Override
  public String create(CreateBuyerTokenCmd cmd) {
    log.info("method: create, cmd: {}", cmd);

    return txManager.doInTx(() -> {
      var facebookUser = socialGateway.getUser(cmd.getFbAccessToken());

      return buyerRepository.findByEmail(facebookUser.getEmail())
          .or(() -> {
            var command = new CreateBuyerCmd()
                .setEmail(facebookUser.getEmail())
                .setSocialId(facebookUser.getId())
                .setSocialPlatform(SocialPlatform.FACEBOOK)
                .setBuyerName(facebookUser.getFullName());

            var buyerId = buyerAppService.create(command);

            return buyerRepository.findById(buyerId);
          })
          .map(Buyer::getBuyerId)
          .map(this::createToken)
          .orElseThrow(() ->
              new DomainException(DomainCode.SOCIAL_LOGIN_FAILED));
    });
  }

  @Override
  public String createWithPassword(CreateBuyerTokenWithPasswordCmd cmd) {
    log.info("method: create, email: {}", cmd.getEmail());

    String token = txManager.doInTx(() -> {
      var buyer = buyerRepository
          .findByEmail(cmd.getEmail())
          .filter(Buyer::active)
          .orElseThrow(() -> new DomainException(DomainCode.INVALID_CREDENTIALS, cmd.getEmail()));

      if (!encoder.matches(cmd.getPassword(), buyer.getPassword())) {
        throw new DomainException(DomainCode.INVALID_CREDENTIALS, cmd.getEmail());
      }

      return createToken(buyer.getBuyerId());
    });

    log.info("method: create");

    return token;
  }
}
