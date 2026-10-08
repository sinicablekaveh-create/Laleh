import { jsonResponse, relatedContract } from "../../../../../lib/api-contracts";
import { localCatalog } from "../../../../../lib/local-catalog";

export async function GET(
  request: Request,
  context: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await context.params;
  const limit = new URL(request.url).searchParams.get("limit");
  return jsonResponse(relatedContract(localCatalog, id, limit));
}
