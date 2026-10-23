/** Application bindings; D1 is optional until configured for the hosted Site. */
declare namespace Cloudflare {
  interface Env {
    DB?: D1Database;
    SIMULATION_API_URL?: string;
  }
}
