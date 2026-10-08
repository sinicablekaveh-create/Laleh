import { healthContract, jsonResponse } from "../../../lib/api-contracts";
import { localCatalog } from "../../../lib/local-catalog";

export function GET(): Response {
  return jsonResponse(healthContract(localCatalog));
}
