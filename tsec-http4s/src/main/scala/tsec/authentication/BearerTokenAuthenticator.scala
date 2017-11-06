package tsec.authentication

import java.time.Instant

import cats.MonadError
import cats.data.OptionT
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.http4s.{AuthScheme, Credentials, Request, Response}
import tsec.cipher.symmetric._
import tsec.cipher.symmetric.imports._
import tsec.common._
import tsec.messagedigests._
import tsec.messagedigests.imports._
import cats.syntax.all._

import scala.concurrent.duration._

sealed abstract class BearerTokenAuthenticator[F[_], I, V] extends Authenticator[F, I, V, TSecBearerToken[I]] {

  def withIdentityStore(newStore: BackingStore[F, I, V]): BearerTokenAuthenticator[F, I, V]

  def withTokenStore(
      newStore: BackingStore[F, SecureRandomId, TSecBearerToken[I]]
  ): BearerTokenAuthenticator[F, I, V]
}

final case class TSecBearerToken[I](
    id: SecureRandomId,
    messageId: I,
    expiry: Instant,
    lastTouched: Option[Instant]
) {
  def isExpired(now: Instant): Boolean = expiry.isBefore(now)
  def isTimedout(now: Instant, timeOut: FiniteDuration): Boolean =
    lastTouched.exists(
      _.plusSeconds(timeOut.toSeconds)
        .isBefore(now)
    )
}

object BearerTokenAuthenticator {
  def apply[F[_], I, V](
      tokenStore: BackingStore[F, SecureRandomId, TSecBearerToken[I]],
      identityStore: BackingStore[F, I, V],
      settings: TSecTokenSettings,
  )(implicit M: MonadError[F, Throwable]): BearerTokenAuthenticator[F, I, V] =
    new BearerTokenAuthenticator[F, I, V] {

      def withIdentityStore(newStore: BackingStore[F, I, V]): BearerTokenAuthenticator[F, I, V] =
        apply(tokenStore, newStore, settings)

      def withTokenStore(
          newStore: BackingStore[F, SecureRandomId, TSecBearerToken[I]]
      ): BearerTokenAuthenticator[F, I, V] =
        apply(newStore, identityStore, settings)

      private def validate(token: TSecBearerToken[I]) = {
        val now = Instant.now()
        !token.isExpired(now) && settings.maxIdle.forall(!token.isTimedout(now, _))
      }

      def extractAndValidate(request: Request[F]): OptionT[F, SecuredRequest[F, TSecBearerToken[I], V]] =
        for {
          rawToken  <- OptionT.fromOption[F](extractBearerToken[F](request))
          token     <- tokenStore.get(SecureRandomId.coerce(rawToken))
          _         <- if (validate(token)) OptionT.pure(()) else OptionT.none
          refreshed <- refresh(token)
          identity  <- identityStore.get(token.messageId)
        } yield SecuredRequest(request, refreshed, identity)

      def create(body: I): OptionT[F, TSecBearerToken[I]] = {
        val newID = SecureRandomId.generate
        val now   = Instant.now()
        val newToken: TSecBearerToken[I] = TSecBearerToken(
          newID,
          body,
          now.plusSeconds(settings.expirationTime.toSeconds),
          settings.maxIdle.map(_ => now)
        )
        OptionT.liftF(tokenStore.put(newToken))
      }

      def update(authenticator: TSecBearerToken[I]): OptionT[F, TSecBearerToken[I]] =
        OptionT.liftF(tokenStore.update(authenticator))

      def discard(authenticator: TSecBearerToken[I]): OptionT[F, TSecBearerToken[I]] =
        OptionT.liftF(tokenStore.delete(authenticator.id)).map(_ => authenticator)

      def renew(authenticator: TSecBearerToken[I]): OptionT[F, TSecBearerToken[I]] = {
        val now = Instant.now()
        val newToken = authenticator.copy(
          expiry = now.plusSeconds(settings.expirationTime.toSeconds),
          lastTouched = settings.maxIdle.map(_ => now)
        )
        update(newToken)
      }

      def refresh(authenticator: TSecBearerToken[I]): OptionT[F, TSecBearerToken[I]] = settings.maxIdle match {
        case None =>
          OptionT.pure(authenticator)
        case Some(idleTime) =>
          val now = Instant.now()
          update(authenticator.copy(lastTouched = Some(now.plusSeconds(idleTime.toSeconds))))
      }

      def embed(response: Response[F], authenticator: TSecBearerToken[I]): Response[F] =
        response.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, authenticator.id)))

      def afterBlock(response: Response[F], authenticator: TSecBearerToken[I]): OptionT[F, Response[F]] =
        settings.maxIdle match {
          case Some(_) =>
            OptionT.pure[F](embed(response, authenticator))
          case None =>
            OptionT.pure[F](response)
        }
    }
}
