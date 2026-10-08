import { categoriesContract, jsonResponse } from "../../../lib/api-contracts";
import { localCatalog } from "../../../lib/local-catalog";

export function GET(request: Request): Promise<Response> {
  return jsonResponse(categoriesContract(localCatalog), { request, publicCache: true });
}
