package tsec.passwordhashers.imports

import tsec.passwordhashers.core._

sealed trait HardenedSCrypt

object HardenedSCrypt extends JCAPasswordPlatform[HardenedSCrypt] {

  private[tsec] def unsafeHashpw(p: Array[Byte]): String =
    SCryptUtil.scrypt(p, SCryptHardenedN, SCryptHardenedR, SCryptHardenedP)

  private[tsec] def unsafeCheckpw(p: Array[Byte], hash: PasswordHash[HardenedSCrypt]): Boolean =
    SCryptUtil.check(p, hash)
}
