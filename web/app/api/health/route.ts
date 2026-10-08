import { healthContract, jsonResponse } from "../../../lib/api-contracts";
import { localCatalog } from "../../../lib/local-catalog";

export function GET(): Promise<Response> {
  // Operational health must remain fresh rather than entering a shared cache.
  return jsonResponse(healthContract(localCatalog));
}
