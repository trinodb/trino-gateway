export enum StoreKey {
  Access = "access-control",
  Config = "app-config",
}

export const ACTION = {
    CREATE: "CREATE",
    UPDATE: "UPDATE",
    DELETE: "DELETE",
    ACTIVATE: "ACTIVATE",
    DEACTIVATE: "DEACTIVATE"
} as const;

export const COMMENT_VALIDATION_RULES = [
    { required: true, message: 'Comment is required' },
    { type: 'string' as const, message: 'type error' },
    { validator: (_rule: any, value: string) => !value || value.trim() !== "", message: 'Comment cannot be empty' },
    { validator: (_rule: any, value: string) => !value || value.length <= 1024, message: 'Comment too long (max 1024 char)' }
];
