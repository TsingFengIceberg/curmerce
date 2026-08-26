"use client";

import { useParams } from "next/navigation";
import { PersonalListingEditor } from "@/components/personal-listing-editor";

export default function EditPersonalListingPage() {
  const params = useParams<{ id: string }>();
  return <PersonalListingEditor listingId={Number(params.id)} />;
}
