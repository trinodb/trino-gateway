export class SessionManager {
    private static instance: SessionManager;
    private timeoutId: number | null = null;
    private readonly TIMEOUT_MINUTES = 15;
    private readonly CHECK_INTERVAL = 60000; // Check every minute
    private lastActivity: number = Date.now();
    private onSessionExpired?: () => void;

    private constructor() {
        this.setupActivityListeners();
        this.startTimeoutCheck();
    }

    public static getInstance(): SessionManager {
        if (!SessionManager.instance) {
            SessionManager.instance = new SessionManager();
        }
        return SessionManager.instance;
    }

    public setSessionExpiredCallback(callback: () => void): void {
        this.onSessionExpired = callback;
    }

    public resetTimeout(): void {
        this.lastActivity = Date.now();
    }

    public clearTimeout(): void {
        if (this.timeoutId) {
            clearInterval(this.timeoutId);
            this.timeoutId = null;
        }
    }

    private setupActivityListeners(): void {
        const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];

        events.forEach(event => {
            document.addEventListener(event, () => {
                this.resetTimeout();
            }, true);
        });
    }

    private startTimeoutCheck(): void {
        this.timeoutId = setInterval(() => {
            const now = Date.now();
            const timeSinceLastActivity = now - this.lastActivity;
            const timeoutMs = this.TIMEOUT_MINUTES * 60 * 1000;

            if (timeSinceLastActivity >= timeoutMs) {
                this.handleSessionExpired();
            }
        }, this.CHECK_INTERVAL);
    }

    private handleSessionExpired(): void {
        this.clearTimeout();
        if (this.onSessionExpired) {
            this.onSessionExpired();
        }
    }

    public isTokenExpired(token: string): boolean {
        if (!token) return true;

        try {
            // Decode JWT token to check expiration
            const payload = JSON.parse(atob(token.split('.')[1]));
            const currentTime = Math.floor(Date.now() / 1000);

            // Check if token has expired
            if (payload.exp && payload.exp < currentTime) {
                return true;
            }

            return false;
        } catch (error) {
            console.error('Error decoding token:', error);
            return true;
        }
    }

    public checkServerRestart(token: string, currentServerStart: number): boolean {
        if (!token) return false;

        try {
            const payload = JSON.parse(atob(token.split('.')[1]));

            // Check if token was issued before server restart
            if (payload.server_start && payload.server_start !== currentServerStart) {
                return true;
            }

            return false;
        } catch (error) {
            console.error('Error checking server restart:', error);
            return false;
        }
    }
}
