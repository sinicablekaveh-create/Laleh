import approvedRows from "../data/public-groups.json" with { type: "json" };
import { CatalogService } from "./catalog-service.ts";

export const localCatalog = new CatalogService(approvedRows);
