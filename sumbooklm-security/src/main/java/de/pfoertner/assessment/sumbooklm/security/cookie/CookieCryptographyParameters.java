package de.pfoertner.assessment.sumbooklm.security.cookie;

/**
 * The parameters a client encrypts and decrypts its stored token pair with.
 *
 * <h2>Role of the Initialization Vector</h2>
 * The vector below is meant for the next encryption the client performs, and it is freshly generated
 * for every request, because reusing a vector with the same key defeats the authentication of the
 * cipher. Decryption does not use it: a client stores the vector it encrypted with in front of the
 * ciphertext and reads it back from there.
 *
 * @param cookieName                 name of the cookie the client stores its token pair in
 * @param algorithm                  name of the cipher the parameters apply to
 * @param keyLength                  length of the key in bits
 * @param initializationVectorLength expected length of an initialization vector in bytes
 * @param authenticationTagLength    length of the authentication tag in bits
 * @param key                        Base64 encoded key derived for the calling client
 * @param initializationVector       Base64 encoded vector to use for the next encryption
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record CookieCryptographyParameters(String cookieName,
                                           String algorithm,
                                           int keyLength,
                                           int initializationVectorLength,
                                           int authenticationTagLength,
                                           String key,
                                           String initializationVector) {
}
