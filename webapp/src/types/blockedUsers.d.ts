export interface BlockedUser {
  username: string;
  scopeType: string;
  scopeValue: string;
  reason?: string;
  blockedBy?: string;
  blockedAt: string;
}
