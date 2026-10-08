import { jsonResponse, searchContract } from "../../../lib/api-contracts";
import { localCatalog } from "../../../lib/local-catalog";

export function GET(request: Request): Promise<Response> {
  // Search terms are user-provided, so shared caching stays disabled for privacy.
  return jsonResponse(searchContract(localCatalog, new URL(request.url).searchParams), { request });
}
