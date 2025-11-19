import { Droit } from "@model/agent/enum/Droit";
import { FicheActe } from "@model/etatcivil/acte/FicheActe";
import { useContext, useMemo, useState } from "react";
import { ECleOngletsMiseAJour, EditionMiseAJourContext } from "../../../../contexts/EditionMiseAJourContextProvider";
import { RECEContextData } from "../../../../contexts/RECEContextProvider";
import AfficherMessage from "../../../../utils/AfficherMessage";
import { estActeEligibleFormuleDIntegration } from "../../../../views/pages/fiche/FichePage";
import Bouton from "../../../commun/bouton/Bouton";
import ConteneurModale from "../../../commun/conteneurs/modale/ConteneurModale";
import SignatureDocument from "../../../commun/signature/SignatureDocument";
import { IMentionMiseAJour } from "../PartieFormulaire";

interface IBoutonTerminerEtSignerProps {
  saisieMentionEnCours: boolean;
  mentionsDeLActe: IMentionMiseAJour[];
  acteEstEligibleFormuleDIntegrationEtUtilisateurALesDroits: boolean;
  acte: FicheActe | null;
}

const BoutonTerminerEtSigner: React.FC<IBoutonTerminerEtSignerProps> = ({
  saisieMentionEnCours,
  mentionsDeLActe,
  acteEstEligibleFormuleDIntegrationEtUtilisateurALesDroits,
  acte
}) => {
  const { utilisateurConnecte } = useContext(RECEContextData);
  const { idActe, idRequete, miseAJourEffectuee } = useContext(EditionMiseAJourContext.Valeurs);
  const { setEstActeSigne, desactiverBlocker, changerOnglet } = useContext(EditionMiseAJourContext.Actions);
  const aDroitSigner = useMemo<boolean>(
    () => utilisateurConnecte.estHabilitePour({ tousLesDroits: [Droit.SIGNER_MENTION, Droit.METTRE_A_JOUR_ACTE] }),
    [utilisateurConnecte]
  );
  const [modaleOuverte, setModaleOuverte] = useState<boolean>(false);

  const typeSignature = useMemo<"MISE_A_JOUR" | "DOUBLE_NUMERIQUE">(() => {
    if (acteEstEligibleFormuleDIntegrationEtUtilisateurALesDroits && acte && estActeEligibleFormuleDIntegration(acte)) {
      return "DOUBLE_NUMERIQUE";
    }
    return "MISE_A_JOUR";
  }, [acteEstEligibleFormuleDIntegrationEtUtilisateurALesDroits, acte]);

  const doitAvoirAuMoinsUneMention = acteEstEligibleFormuleDIntegrationEtUtilisateurALesDroits;
  const aAuMoinsUneMention = mentionsDeLActe.length > 0;
  const peutSigner = !doitAvoirAuMoinsUneMention || aAuMoinsUneMention;

  const handleClick = () => {
    if (!peutSigner) {
      AfficherMessage.erreur("Au moins une mention apposée manuellement doit être ajoutée avant de pouvoir signer.");
      return;
    }
    setModaleOuverte(true);
  };

  if (!aDroitSigner) return <></>;

  return (
    <>
      <Bouton
        type="button"
        title="Terminer et signer"
        onClick={handleClick}
        disabled={saisieMentionEnCours || !miseAJourEffectuee || !peutSigner}
      >
        {"Terminer et signer"}
      </Bouton>

      {modaleOuverte && (
        <ConteneurModale>
          <div className="border-3 w-[34rem] max-w-full rounded-xl border-solid border-bleu-sombre bg-blanc p-5">
            <h2 className="m-0 mb-4 text-center font-medium text-bleu-sombre">{"Signature des mentions"}</h2>
            <SignatureDocument
              typeSignature={typeSignature}
              idActe={idActe}
              idRequete={idRequete}
              apresSignature={(succes: boolean) => {
                setModaleOuverte(false);
                if (!succes) return;

                changerOnglet(ECleOngletsMiseAJour.ACTE, null);
                setEstActeSigne(true);
                AfficherMessage.succes("L'acte a été mis à jour avec succès.", { fermetureAuto: true });
                desactiverBlocker();
              }}
            />
          </div>
        </ConteneurModale>
      )}
    </>
  );
};

export default BoutonTerminerEtSigner;
