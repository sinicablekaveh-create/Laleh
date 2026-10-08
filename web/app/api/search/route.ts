import { jsonResponse, searchContract } from "../../../lib/api-contracts";
import { localCatalog } from "../../../lib/local-catalog";

export function GET(request: Request): Response {
  return jsonResponse(searchContract(localCatalog, new URL(request.url).searchParams));
}
