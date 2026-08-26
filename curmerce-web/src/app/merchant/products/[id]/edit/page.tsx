"use client";

import { useParams } from "next/navigation";
import { MerchantProductEditor } from "@/components/merchant-product-editor";

export default function EditMerchantProductPage() {
  const params = useParams<{ id: string }>();
  return <MerchantProductEditor productId={Number(params.id)} />;
}
