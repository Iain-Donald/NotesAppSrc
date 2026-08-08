#include <jni.h>
#include <string.h>
#include <sodium.h>

#define JNI_FN(name) Java_com_example_iainnotes_crypto_Sodium_##name

JNIEXPORT jint JNICALL JNI_FN(init)(JNIEnv *e, jclass c) {
(void)e; (void)c;
return sodium_init();          // 0 = ok, 1 = already, -1 = fail
}

JNIEXPORT void JNICALL JNI_FN(randomBytes)(JNIEnv *e, jclass c, jbyteArray out) {
(void)c;
jsize n = (*e)->GetArrayLength(e, out);
jbyte *p = (*e)->GetByteArrayElements(e, out, NULL);
randombytes_buf(p, (size_t)n);
(*e)->ReleaseByteArrayElements(e, out, p, 0);
}

/* Argon2id. Returns 0 on success, -1 on failure (typically OOM). */
JNIEXPORT jint JNICALL JNI_FN(pwhash)(
		JNIEnv *e, jclass c,
		jbyteArray out, jbyteArray passwd, jbyteArray salt,
jlong opslimit, jlong memlimitKib) {
(void)c;
jsize outLen = (*e)->GetArrayLength(e, out);
jsize pwLen  = (*e)->GetArrayLength(e, passwd);

jbyte *o  = (*e)->GetByteArrayElements(e, out, NULL);
jbyte *pw = (*e)->GetByteArrayElements(e, passwd, NULL);
jbyte *s  = (*e)->GetByteArrayElements(e, salt, NULL);

int rc = crypto_pwhash(
		(unsigned char *)o, (unsigned long long)outLen,
		(const char *)pw, (unsigned long long)pwLen,
		(const unsigned char *)s,
		(unsigned long long)opslimit,
		(size_t)memlimitKib * 1024U,
crypto_pwhash_ALG_ARGON2ID13);

sodium_memzero(pw, (size_t)pwLen);
(*e)->ReleaseByteArrayElements(e, passwd, pw, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, salt, s, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, out, o, 0);
return rc;
}

/* Returns ciphertext+tag, or NULL on failure. */
JNIEXPORT jbyteArray JNICALL JNI_FN(aeadEncrypt)(
		JNIEnv *e, jclass c,
		jbyteArray msg, jbyteArray ad, jbyteArray nonce, jbyteArray key) {
(void)c;
jsize mLen  = (*e)->GetArrayLength(e, msg);
jsize adLen = ad ? (*e)->GetArrayLength(e, ad) : 0;

jbyteArray out = (*e)->NewByteArray(
		e, mLen + crypto_aead_xchacha20poly1305_ietf_ABYTES);
if (!out) return NULL;

jbyte *m  = (*e)->GetByteArrayElements(e, msg, NULL);
jbyte *a  = ad ? (*e)->GetByteArrayElements(e, ad, NULL) : NULL;
jbyte *n  = (*e)->GetByteArrayElements(e, nonce, NULL);
jbyte *k  = (*e)->GetByteArrayElements(e, key, NULL);
jbyte *o  = (*e)->GetByteArrayElements(e, out, NULL);

unsigned long long oLen = 0;
int rc = crypto_aead_xchacha20poly1305_ietf_encrypt(
		(unsigned char *)o, &oLen,
		(const unsigned char *)m, (unsigned long long)mLen,
		(const unsigned char *)a, (unsigned long long)adLen,
		NULL,
		(const unsigned char *)n, (const unsigned char *)k);

(*e)->ReleaseByteArrayElements(e, msg, m, JNI_ABORT);
if (a) (*e)->ReleaseByteArrayElements(e, ad, a, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, nonce, n, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, key, k, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, out, o, 0);
return rc == 0 ? out : NULL;
}

/* Returns plaintext, or NULL if authentication fails. */
JNIEXPORT jbyteArray JNICALL JNI_FN(aeadDecrypt)(
		JNIEnv *e, jclass c,
		jbyteArray ct, jbyteArray ad, jbyteArray nonce, jbyteArray key) {
(void)c;
jsize ctLen = (*e)->GetArrayLength(e, ct);
jsize adLen = ad ? (*e)->GetArrayLength(e, ad) : 0;
if (ctLen < (jsize)crypto_aead_xchacha20poly1305_ietf_ABYTES) return NULL;

jbyteArray out = (*e)->NewByteArray(
		e, ctLen - crypto_aead_xchacha20poly1305_ietf_ABYTES);
if (!out) return NULL;

jbyte *ctp = (*e)->GetByteArrayElements(e, ct, NULL);
jbyte *a   = ad ? (*e)->GetByteArrayElements(e, ad, NULL) : NULL;
jbyte *n   = (*e)->GetByteArrayElements(e, nonce, NULL);
jbyte *k   = (*e)->GetByteArrayElements(e, key, NULL);
jbyte *o   = (*e)->GetByteArrayElements(e, out, NULL);

unsigned long long oLen = 0;
int rc = crypto_aead_xchacha20poly1305_ietf_decrypt(
		(unsigned char *)o, &oLen,
		NULL,
		(const unsigned char *)ctp, (unsigned long long)ctLen,
		(const unsigned char *)a, (unsigned long long)adLen,
		(const unsigned char *)n, (const unsigned char *)k);

(*e)->ReleaseByteArrayElements(e, ct, ctp, JNI_ABORT);
if (a) (*e)->ReleaseByteArrayElements(e, ad, a, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, nonce, n, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, key, k, JNI_ABORT);
(*e)->ReleaseByteArrayElements(e, out, o, rc == 0 ? 0 : JNI_ABORT);
return rc == 0 ? out : NULL;
}

JNIEXPORT void JNICALL JNI_FN(memzero)(JNIEnv *e, jclass c, jbyteArray buf) {
(void)c;
	jsize n = (*e)->GetArrayLength(e, buf);
	jbyte *p = (*e)->GetByteArrayElements(e, buf, NULL);
	sodium_memzero(p, (size_t)n);
	(*e)->ReleaseByteArrayElements(e, buf, p, 0);
}