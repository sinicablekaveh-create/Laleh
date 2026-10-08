import { categoriesContract, jsonResponse } from "../../../lib/api-contracts";
import { localCatalog } from "../../../lib/local-catalog";

export function GET(): Response {
  return jsonResponse(categoriesContract(localCatalog));
}
