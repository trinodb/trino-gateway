import { BlockedUser } from "../../types/blockedUsers";
import { api } from "../base";

export async function listBlockedUsersApi(body: Record<string, any>): Promise<BlockedUser[]> {
  return api.post('/webapp/getBlockedUsers', body)
}

export async function blockUserApi(body: Record<string, any>): Promise<BlockedUser> {
  return api.post('/webapp/blockUser', body)
}

export async function unblockUserApi(body: Record<string, any>): Promise<boolean> {
  return api.post('/webapp/unblockUser', body)
}
