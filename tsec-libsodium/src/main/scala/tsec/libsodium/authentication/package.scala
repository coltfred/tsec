package tsec.libsodium

import tsec.libsodium.authentication.internal.SodiumMacAlgo

package object authentication {

  type SodiumMACKey[A] = SodiumMACKey.Type[A]

  object SodiumMACKey {
    type Type[A] <: Array[Byte]

    def apply[A: SodiumMacAlgo](bytes: Array[Byte]): SodiumMACKey[A] =
      bytes.asInstanceOf[SodiumMACKey[A]]

    def subst[A]: PartiallyApplied[A] = new PartiallyApplied[A]

    private[tsec] final class PartiallyApplied[A](val dummy: Boolean = true) extends AnyVal {
      def apply[F[_]](value: F[Array[Byte]]): F[SodiumMACKey[A]] = value.asInstanceOf[F[SodiumMACKey[A]]]
    }

    def unsubst[A]: PartiallyUnapplied[A] = new PartiallyUnapplied[A]

    private[tsec] final class PartiallyUnapplied[A](val dummy: Boolean = true) extends AnyVal {
      def apply[F[_]](value: F[SodiumMACKey[A]]): F[Array[Byte]] = value.asInstanceOf[F[Array[Byte]]]
    }
  }

}
