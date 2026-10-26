/** Plain configuration the use cases need, bound from the env config in NotificationModule. */
export interface NotificationSettings {
  /** Receives the contract.disputed email (ADMIN_EMAIL). */
  adminEmail: string;
}

export const NOTIFICATION_SETTINGS = Symbol('NOTIFICATION_SETTINGS');
