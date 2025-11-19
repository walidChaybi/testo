import { ETATCIVIL_API } from "@api/ApiDisponibles";
import { TConfigurationApi } from "@model/api/Api";

const URI = "/acte/:idActe/double-numerique/composer-document-final";

interface IBody {
  issuerCertificat: string;
  entiteCertificat: string;
}

export const CONFIG_PATCH_COMPOSER_DOCUMENT_FINAL_DOUBLE_NUMERIQUE: TConfigurationApi<typeof URI, IBody, undefined, string> = {
  api: ETATCIVIL_API,
  methode: "PATCH",
  uri: URI,
  avecAxios: true
};
