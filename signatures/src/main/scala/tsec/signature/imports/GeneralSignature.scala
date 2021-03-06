package tsec.signature.imports

import java.security.spec._
import java.security.{KeyFactory, KeyPairGenerator}

import cats.Id
import cats.effect.Sync
import cats.syntax.either._
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import tsec.common.ErrorConstruct._
import tsec.signature.core._

abstract class GeneralSignature[A](signature: String, kfAlgo: String)
    extends JCASigTag[A]
    with KFTag[A]
    with CertSignatureAPI[A, SigPublicKey, SigPrivateKey, SigCertificate] {

  private[tsec] def keyFactoryAlgo: String = kfAlgo

  val algorithm: String          = signature
  implicit val sig: JCASigTag[A] = this

  implicit val kt: KFTag[A] = this
  implicit def genSigAsymmGen[F[_]](implicit F: Sync[F]): JCASigKG[F, A] =
    new JCASigKG[F, A] {
      def generateKeyPair: F[SigKeyPair[A]] =
        F.delay(impl.generateKeyPairUnsafe)

      def buildPrivateKey(rawPk: Array[Byte]): F[SigPrivateKey[A]] =
        F.delay(impl.buildPrivateKeyUnsafe(rawPk))

      def buildPublicKey(rawPk: Array[Byte]): F[SigPublicKey[A]] =
        F.delay(impl.buildPublicKeyUnsafe(rawPk))
    }

  implicit val SigKeyGenEither: JCASigKG[SigErrorM, A] =
    new JCASigKG[SigErrorM, A] {

      def generateKeyPair: SigErrorM[SigKeyPair[A]] =
        impl.generateKeyPair

      def buildPrivateKey(rawPk: Array[Byte]): SigErrorM[SigPrivateKey[A]] =
        impl.buildPrivateKey(rawPk)

      def buildPublicKey(rawPk: Array[Byte]): SigErrorM[SigPublicKey[A]] =
        impl.buildPublicKey(rawPk)
    }

  implicit val SigKeyGenId: JCASigKG[Id, A] = new JCASigKG[Id, A] {

    def generateKeyPair: Id[SigKeyPair[A]] = impl.generateKeyPairUnsafe

    def buildPrivateKey(rawPk: Array[Byte]): Id[SigPrivateKey[A]] =
      impl.buildPrivateKeyUnsafe(rawPk)

    def buildPublicKey(rawPk: Array[Byte]): Id[SigPublicKey[A]] =
      impl.buildPublicKeyUnsafe(rawPk)
  }

  object impl {

    def generateKeyPair: Either[SignatureKeyError, SigKeyPair[A]] =
      Either.catchNonFatal(generateKeyPairUnsafe).mapError(SignatureKeyError.apply)

    def generateKeyPairUnsafe: SigKeyPair[A] =
      SigKeyPair.fromKeyPair[A](KeyPairGenerator.getInstance(kfAlgo, "BC").generateKeyPair())

    def buildPrivateKey(keyBytes: Array[Byte]): Either[SignatureKeyError, SigPrivateKey[A]] =
      Either.catchNonFatal(buildPrivateKeyUnsafe(keyBytes)).mapError(SignatureKeyError.apply)

    def buildPrivateKeyUnsafe(keyBytes: Array[Byte]): SigPrivateKey[A] =
      SigPrivateKey[A](
        KeyFactory.getInstance(kfAlgo).generatePrivate(new PKCS8EncodedKeySpec(keyBytes))
      )

    def buildPublicKey(keyBytes: Array[Byte]): Either[SignatureKeyError, SigPublicKey[A]] =
      Either.catchNonFatal(buildPublicKeyUnsafe(keyBytes)).mapError(SignatureKeyError.apply)

    def buildPublicKeyUnsafe(keyBytes: Array[Byte]): SigPublicKey[A] =
      SigPublicKey[A](KeyFactory.getInstance(kfAlgo).generatePublic(new X509EncodedKeySpec(keyBytes)))
  }
}

abstract class RSASignature[A](signature: String)
    extends RSAKFTag[A]
    with JCASigTag[A]
    with CertSignatureAPI[A, SigPublicKey, SigPrivateKey, SigCertificate] {

  override lazy val algorithm: String = signature

  private val defaultKeySize = 2048
  private val strongKeySize  = 4096

  implicit val sig: JCASigTag[A] = this

  implicit val kt: RSAKFTag[A] = this

  private[tsec] def keyFactoryAlgo: String = impl.KeyFactoryAlgo

  implicit def genSigAsymmGen[F[_]](implicit F: Sync[F]): JCARSASigKG[F, A] =
    new JCARSASigKG[F, A] {
      def generateKeyPair: F[SigKeyPair[A]] =
        F.delay(impl.generateKeyPairUnsafe)

      def buildPrivateKey(rawPk: Array[Byte]): F[SigPrivateKey[A]] =
        F.delay(impl.buildPrivateKeyUnsafe(rawPk))

      def buildPublicKey(rawPk: Array[Byte]): F[SigPublicKey[A]] =
        F.delay(impl.buildPublicKeyUnsafe(rawPk))

      def generateKeyPairStrong: F[SigKeyPair[A]] =
        F.delay(impl.generateKeyPairStrongUnsafe)
    }

  implicit val SigKeyGenEither: JCARSASigKG[SigErrorM, A] =
    new JCARSASigKG[SigErrorM, A] {

      def generateKeyPair: SigErrorM[SigKeyPair[A]] =
        impl.generateKeyPair

      def buildPrivateKey(rawPk: Array[Byte]): SigErrorM[SigPrivateKey[A]] =
        impl.buildPrivateKey(rawPk)

      def buildPublicKey(rawPk: Array[Byte]): SigErrorM[SigPublicKey[A]] =
        impl.buildPublicKey(rawPk)

      def generateKeyPairStrong: SigErrorM[SigKeyPair[A]] =
        impl.generateKeyPairStrong
    }

  implicit val SigKeyGenId: JCARSASigKG[Id, A] = new JCARSASigKG[Id, A] {

    def generateKeyPair: Id[SigKeyPair[A]] = impl.generateKeyPairUnsafe

    def buildPrivateKey(rawPk: Array[Byte]): Id[SigPrivateKey[A]] =
      impl.buildPrivateKeyUnsafe(rawPk)

    def buildPublicKey(rawPk: Array[Byte]): Id[SigPublicKey[A]] =
      impl.buildPublicKeyUnsafe(rawPk)

    def generateKeyPairStrong: Id[SigKeyPair[A]] =
      impl.generateKeyPairStrongUnsafe
  }

  object impl {
    val KeyFactoryAlgo: String = "RSA"

    def generateKeyPair: Either[SignatureKeyError, SigKeyPair[A]] =
      Either.catchNonFatal(generateKeyPairUnsafe).mapError(SignatureKeyError.apply)

    def generateKeyPairUnsafe: SigKeyPair[A] = {
      val instance = KeyPairGenerator.getInstance(KeyFactoryAlgo)
      instance.initialize(defaultKeySize)
      SigKeyPair.fromKeyPair[A](instance.generateKeyPair())
    }

    def generateKeyPairStrong: Either[SignatureKeyError, SigKeyPair[A]] =
      Either
        .catchNonFatal(generateKeyPairStrongUnsafe)
        .leftMap(e => SignatureKeyError(e.getMessage))

    def generateKeyPairStrongUnsafe: SigKeyPair[A] = {
      val instance = KeyPairGenerator.getInstance(KeyFactoryAlgo)
      instance.initialize(strongKeySize)
      SigKeyPair.fromKeyPair[A](instance.generateKeyPair())
    }

    def buildPrivateKey(keyBytes: Array[Byte]): Either[SignatureKeyError, SigPrivateKey[A]] =
      Either.catchNonFatal(buildPrivateKeyUnsafe(keyBytes)).mapError(SignatureKeyError.apply)

    def buildPrivateKeyUnsafe(keyBytes: Array[Byte]): SigPrivateKey[A] =
      SigPrivateKey[A](
        KeyFactory.getInstance(KeyFactoryAlgo).generatePrivate(new PKCS8EncodedKeySpec(keyBytes))
      )

    def buildPublicKey(keyBytes: Array[Byte]): Either[SignatureKeyError, SigPublicKey[A]] =
      Either.catchNonFatal(buildPublicKeyUnsafe(keyBytes)).mapError(SignatureKeyError.apply)

    def buildPublicKeyUnsafe(keyBytes: Array[Byte]): SigPublicKey[A] =
      SigPublicKey[A](
        KeyFactory
          .getInstance(KeyFactoryAlgo)
          .generatePublic(new X509EncodedKeySpec(keyBytes))
      )
  }
}

abstract class ECDSASignature[A](signature: String, dCurve: String, outLen: Int)
    extends JCASigTag[A]
    with ECCurve[A]
    with ECKFTag[A]
    with CertSignatureAPI[A, SigPublicKey, SigPrivateKey, SigCertificate] {

  override lazy val algorithm: String = signature

  protected val defaultCurve: String = dCurve

  private[tsec] def keyFactoryAlgo: String = impl.KeyFactoryAlgo

  val outputLen: Int = outLen

  implicit val sig: JCASigTag[A] = this

  implicit val curve: ECCurve[A] = this

  implicit val kt: ECKFTag[A] = this

  implicit def genSigAsymmGen[F[_]](implicit F: Sync[F]): JCAECKG[F, A] =
    new JCAECKG[F, A] {
      val outputLen: Int = outLen

      def generateKeyPair: F[SigKeyPair[A]] =
        F.delay(impl.generateKeyPairUnsafe)

      def buildPrivateKey(rawPk: Array[Byte]): F[SigPrivateKey[A]] =
        F.delay(impl.buildPrivateKeyUnsafe(rawPk))

      def buildPublicKey(rawPk: Array[Byte]): F[SigPublicKey[A]] =
        F.delay(impl.buildPublicKeyUnsafe(rawPk))

      def buildPrivateKeyFromPoint(S: BigInt): F[SigPrivateKey[A]] =
        F.delay(impl.buildPrivateKeyFromPoint(S))

      def buildPublicKeyFromPoints(x: BigInt, y: BigInt): F[SigPublicKey[A]] =
        F.delay(impl.buildPublicKeyUnsafeFromPoints(x, y))
    }

  implicit val SigKeyGenEither: JCAECKG[SigErrorM, A] =
    new JCAECKG[SigErrorM, A] {

      def generateKeyPair: SigErrorM[SigKeyPair[A]] =
        impl.generateKeyPair

      def buildPrivateKey(rawPk: Array[Byte]): SigErrorM[SigPrivateKey[A]] =
        impl.buildPrivateKey(rawPk)

      def buildPublicKey(rawPk: Array[Byte]): SigErrorM[SigPublicKey[A]] =
        impl.buildPublicKey(rawPk)

      def outputLen: Int = outLen

      def buildPrivateKeyFromPoint(S: BigInt): SigErrorM[SigPrivateKey[A]] =
        impl.buildPrivatefromPoint(S)

      def buildPublicKeyFromPoints(x: BigInt, y: BigInt): SigErrorM[SigPublicKey[A]] =
        impl.buildPublicKeyFromPoints(x, y)
    }

  implicit val SigKeyGenId: JCAECKG[Id, A] = new JCAECKG[Id, A] {

    def generateKeyPair: Id[SigKeyPair[A]] = impl.generateKeyPairUnsafe

    def buildPrivateKey(rawPk: Array[Byte]): Id[SigPrivateKey[A]] =
      impl.buildPrivateKeyUnsafe(rawPk)

    def buildPublicKey(rawPk: Array[Byte]): Id[SigPublicKey[A]] =
      impl.buildPublicKeyUnsafe(rawPk)

    def outputLen: Int = outLen

    def buildPrivateKeyFromPoint(S: BigInt): Id[SigPrivateKey[A]] =
      impl.buildPrivateKeyFromPoint(S)

    def buildPublicKeyFromPoints(x: BigInt, y: BigInt): Id[SigPublicKey[A]] =
      impl.buildPublicKeyUnsafeFromPoints(x, y)
  }

  object impl {
    val KeyFactoryAlgo: String = "ECDSA"

    def generateKeyPair: Either[SignatureKeyError, SigKeyPair[A]] =
      Either.catchNonFatal(generateKeyPairUnsafe).mapError(SignatureKeyError.apply)

    def generateKeyPairUnsafe: SigKeyPair[A] = {
      val instance = KeyPairGenerator.getInstance(KeyFactoryAlgo, ECDSASignature.Provider)
      instance.initialize(new ECGenParameterSpec(defaultCurve))
      SigKeyPair.fromKeyPair[A](instance.generateKeyPair())
    }

    def buildPrivateKey(keyBytes: Array[Byte]): Either[SignatureKeyError, SigPrivateKey[A]] =
      Either.catchNonFatal(buildPrivateKeyUnsafe(keyBytes)).mapError(SignatureKeyError.apply)

    def buildPrivateKeyUnsafe(keyBytes: Array[Byte]): SigPrivateKey[A] =
      SigPrivateKey[A](
        KeyFactory
          .getInstance(KeyFactoryAlgo, ECDSASignature.Provider)
          .generatePrivate(new PKCS8EncodedKeySpec(keyBytes))
      )

    def buildPrivatefromPoint(S: BigInt): Either[SignatureKeyError, SigPrivateKey[A]] =
      Either.catchNonFatal(buildPrivateKeyFromPoint(S)).mapError(SignatureKeyError.apply)

    def buildPrivateKeyFromPoint(S: BigInt): SigPrivateKey[A] = {
      val spec = new ECPrivateKeySpec(S.underlying(), curveSpec)
      SigPrivateKey[A](
        KeyFactory
          .getInstance(KeyFactoryAlgo, ECDSASignature.Provider)
          .generatePrivate(spec)
      )
    }

    private lazy val curveSpec: ECNamedCurveSpec = {
      val paramSpec = ECNamedCurveTable.getParameterSpec(defaultCurve)
      new ECNamedCurveSpec(defaultCurve, paramSpec.getCurve, paramSpec.getG, paramSpec.getN, paramSpec.getH)
    }

    def buildPublicKeyFromPoints(x: BigInt, y: BigInt): Either[SignatureKeyError, SigPublicKey[A]] =
      Either.catchNonFatal(buildPublicKeyUnsafeFromPoints(x, y)).mapError(SignatureKeyError.apply)

    def buildPublicKeyUnsafeFromPoints(x: BigInt, y: BigInt): SigPublicKey[A] = {
      val spec = new ECPublicKeySpec(new ECPoint(x.underlying(), y.underlying()), curveSpec)
      SigPublicKey[A](KeyFactory.getInstance(KeyFactoryAlgo, ECDSASignature.Provider).generatePublic(spec))
    }

    def buildPublicKey(keyBytes: Array[Byte]): Either[SignatureKeyError, SigPublicKey[A]] =
      Either.catchNonFatal(buildPublicKeyUnsafe(keyBytes)).mapError(SignatureKeyError.apply)

    def buildPublicKeyUnsafe(keyBytes: Array[Byte]): SigPublicKey[A] =
      SigPublicKey[A](
        KeyFactory
          .getInstance(KeyFactoryAlgo, ECDSASignature.Provider)
          .generatePublic(new X509EncodedKeySpec(keyBytes))
      )
  }

}

//Todo: abstraction over bouncy presence
object ECDSASignature {
  val Provider = "BC"
}
