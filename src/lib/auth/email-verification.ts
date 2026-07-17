export function isEmailVerificationRequired(): boolean {
  // Secure by default. Local/demo environments may explicitly opt out.
  return process.env.AUTH_REQUIRE_EMAIL_VERIFICATION !== 'false';
}

