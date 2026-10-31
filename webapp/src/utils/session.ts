// JWT payloads are base64url encoded, which atob does not accept as is
function decodePayload(token: string) {
  return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
}

export function isTokenExpired(token: string): boolean {
  if (!token) return true;
  // Non-JWT tokens, such as the username returned in no-auth mode, are left to the server
  if (token.split('.').length !== 3) return false;
  try {
    const { exp } = decodePayload(token);
    return typeof exp === 'number' && exp * 1000 <= Date.now();
  } catch {
    return false;
  }
}
