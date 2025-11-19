package fr.gouv.diplomatie.rece.etatcivil.application.services;

import com.github.f4b6a3.uuid.UuidCreator;
import eu.europa.esig.dss.model.DSSDocument;
import fr.gouv.diplomatie.rece.commun.exceptions.BusinessException;
import fr.gouv.diplomatie.rece.commun.exceptions.TechnicalException;
import fr.gouv.diplomatie.rece.commun.utils.SwiftUtils;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.AnalyseMarginaleTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.HorodatageTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.MentionTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.PersonneTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.PreuveSignatureActeTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.StockageTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.SuiviHorodatageTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.in.SuiviSignatureTraitement;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.CompositionPort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.DeleteMentionPort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.LoadActePort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.LoadAnalyseMarginalePort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.LoadDocumentMentionsPort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.LoadMentionPort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.LoadUtilisateursPort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.SaveDocumentMentionsPort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.UpdateActePort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.UpdateAnalyseMarginalePort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.UpdateDocumentMentionsPort;
import fr.gouv.diplomatie.rece.etatcivil.application.ports.out.UpdateMentionPort;
import fr.gouv.diplomatie.rece.etatcivil.application.services.rules.acteetextrait.etabli.naissance.RegleFormatageTexteActeEtExtraitEtabliNaissance;
import fr.gouv.diplomatie.rece.etatcivil.application.services.utils.CompositionJsonUtils;
import fr.gouv.diplomatie.rece.etatcivil.application.services.utils.DateUtils;
import fr.gouv.diplomatie.rece.etatcivil.application.services.utils.VerificationDroits;
import fr.gouv.diplomatie.rece.etatcivil.domain.enums.BusinessExceptionCode;
import fr.gouv.diplomatie.rece.etatcivil.domain.enums.StatutDocumentMentions;
import fr.gouv.diplomatie.rece.etatcivil.domain.enums.StatutMention;
import fr.gouv.diplomatie.rece.etatcivil.domain.enums.StatutSignature;
import fr.gouv.diplomatie.rece.etatcivil.domain.enums.TechnicalExceptionCode;
import fr.gouv.diplomatie.rece.etatcivil.domain.enums.TypeDocumentComposition;
import fr.gouv.diplomatie.rece.etatcivil.domain.enums.TypeOrigine;
import fr.gouv.diplomatie.rece.etatcivil.domain.habilitation.AdresseService;
import fr.gouv.diplomatie.rece.etatcivil.domain.habilitation.Utilisateur;
import fr.gouv.diplomatie.rece.etatcivil.domain.habilitation.enums.NomDroit;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.acte.Acte;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.acte.AnalyseMarginale;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.commun.AutoriteEtatCivil;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.mention.DocumentMentions;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.mention.Mention;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.personne.Personne;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.signature.DocumentSigne;
import fr.gouv.diplomatie.rece.etatcivil.domain.repertoirecivil.signature.Signature;
import fr.gouv.diplomatie.rece.etatcivil.domain.resultat.ResultatEnregistrerDocumentSwift;
import fr.gouv.diplomatie.rece.etatcivil.domain.resultat.ResultatHorodatageDocumentSigne;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.commons.lang3.StringUtils.SPACE;

@RequiredArgsConstructor
@Service
@Setter
@Slf4j
public class MentionService implements MentionTraitement {

	private final LoadMentionPort loadMentionPort;
	private final UpdateMentionPort updateMentionPort;
	private final DeleteMentionPort deleteMentionPort;
	private final LoadUtilisateursPort loadUtilisateursPort;
	private final LoadActePort loadActePort;
	private final SuiviSignatureTraitement suiviSignature;
	private final CompositionPort compositionPort;

	private final SuiviHorodatageTraitement suiviHorodatage;
	private final HorodatageTraitement horodatageTraitement;
	private final StockageTraitement stockageTraitement;
	private final AnalyseMarginaleTraitement analyseMarginaleTraitement;
	private final SaveDocumentMentionsPort saveDocumentMentionsPort;

	private final PreuveSignatureActeTraitement preuveSignatureActeTraitement;
	private final LoadDocumentMentionsPort loadDocumentMentionsPort;
	private final UpdateDocumentMentionsPort updateDocumentMentionsPort;
	private final LoadAnalyseMarginalePort loadAnalyseMarginalePort;
	private final UpdateAnalyseMarginalePort updateAnalyseMarginalePort;
	private final PersonneTraitement personneTraitement;
	private final UpdateActePort updateActePort;
	private final Clock clock;

	@Value("${signature.blocage.debut.heure}")
	private String heureDebutBlocageSignature;
	@Value("${signature.blocage.fin.heure}")
	private String heureFinBlocageSignature;


	@Override
	public List<Mention> getMentionsByIdActe(UUID idActe, StatutMention statut) {
		if (statut != null) {
			return loadMentionPort.getMentionsByIdActeAndStatut(idActe, statut);
		} else {
			return loadMentionPort.getMentionsByIdActe(idActe);
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void miseAJourMentions(final List<Mention> mentions, final UUID idActe, final String idArobas) {
		// Récupération de l'utilisateur et vérification du droit "délivrer"
		final Utilisateur utilisateur = loadUtilisateursPort.getUtilisateurByIdArobas(idArobas);
		VerificationDroits.exceptionSiUtilisateurNaPasLeDroit(utilisateur, NomDroit.DELIVRER);

		if (mentions.stream().anyMatch(mention -> mention.getTypeMention() == null
				|| mention.getTypeMention().getIdTypeMention() == null)) {
			throw new BusinessException(BusinessExceptionCode.ERREUR_TYPE_MENTION_NULL, idActe);
		}

		// Vérification que l'acte existe
		if (!loadActePort.acteExisteByIdActe(idActe)) {
			throw new BusinessException(BusinessExceptionCode.AUCUN_ACTE, idActe);
		}

		final List<Mention> mentionsBdd = loadMentionPort.getMentionsByIdActe(idActe);

		final List<Mention> listeMajMentions = new ArrayList<>();
		final List<Mention> listeAjoutMentions = new ArrayList<>();

		// on récupère les mentions à mettre à jour
		mentions.forEach(mention -> {
			// Si une mention en entrée n'est pas en base on l'ajoute, l'id qui permet de
			// comparer vient du front et ne sera pas celui en base.
			if (mentionsBdd.stream().noneMatch(m -> m.getId().equals(mention.getId()))) {
				mention.setOrigine(TypeOrigine.RECE);
				listeAjoutMentions.add(mention);
			}
			// Valorisation des mentions à mettre à jour
			mentionsBdd.stream().filter(m -> m.getId().equals(mention.getId())).findFirst()
					   .ifPresent(mentionBdd -> {
						   mention.setOrigine(mentionBdd.getOrigine());
						   listeMajMentions.add(mention);
					   });
			// Filtre des mentions à mettre à jour afin de garder que celle à supprimer
			mentionsBdd.removeIf(m -> m.getId().equals(mention.getId()));

		});

		// Création de la liste des mentions à supprimer filtrée préalablement et
		// seulement si c'est une mention ajoutée lors d'une délivrance : absence de
		// texte mention et présence d'un texte de délivrance
		final List<Mention> listeSupprMentions = mentionsBdd.stream()
															.filter(mention -> mention.getTextes().getTexteMention() == null
																	&& (mention.getTextes().getTexteMentionDelivrance() != null || mention.getTextes().getTexteMentionPlurilingue() != null))
															.toList();

		if (!listeMajMentions.isEmpty()) {
			listeMajMentions.forEach(m -> updateMentionPort.updateMention(m, loadActePort.getNatureActeByIdActe(idActe).toString()));
		}
		if (!listeAjoutMentions.isEmpty()) {
			listeAjoutMentions.forEach(m -> updateMentionPort.addMention(m, idActe));
		}
		if (!listeSupprMentions.isEmpty()) {
			listeSupprMentions.forEach(deleteMentionPort::deleteMention);
		}
	}

	@Override
	public String composerDocumentMentionsUlterieures(UUID idActe, Signature signature, String idArobas) {

		// Vérifier la disponibilité de la signature
		verifierDisponibiliteSignature();
		// Vérifier les droits de l'utilisateur
		Utilisateur utilisateur = verifierDroitsUtilisateur(idArobas);
		// Vérifier la plage horaire de la signature
		verifierPlageHoraireSignature(utilisateur);
		// Récupérer l'acte signé
		Acte acteSigne = getActeSigneById(idActe, idArobas);
		// Valoriser les mentions de l'acte signé dans la base
		valoriserMentionsActe(acteSigne, utilisateur, signature);
		// Composer le pdf des mentions signé
		byte[] documentMentionsUlterieursPDF = composerPdfMentionsUlterieures(acteSigne);
		// Enregistrer le document mentions dans la base
		DocumentMentions documentMentions = enregistrerDocumentMentions(idActe);
		// Enregistrer la preuve pré-signature
		preuveSignatureActeTraitement.enregistrerAvantSignature(documentMentions.getId(), documentMentionsUlterieursPDF);
		// Retourner le document en base 64 pour le signer
		return Base64.encodeBase64String(documentMentionsUlterieursPDF);
	}

	private void verifierDisponibiliteSignature() {
		if (suiviSignature.getStatutSignature() != StatutSignature.DISPONIBLE) {
			throw new TechnicalException(TechnicalExceptionCode.SIGNATURE_INDISPO);
		}
	}

	private Utilisateur verifierDroitsUtilisateur(String idArobas) {
		Utilisateur utilisateur = loadUtilisateursPort.getUtilisateurByIdArobas(idArobas);
		utilisateur.verifierDroit(NomDroit.SIGNER_MENTION);
		return utilisateur;
	}

	private void verifierPlageHoraireSignature(Utilisateur utilisateur) {

		LocalTime heureDebutBlocage = LocalTime.parse(heureDebutBlocageSignature);
		LocalTime heureFinBlocage = LocalTime.parse(heureFinBlocageSignature);

		// Obtenir le fuseau horaire de l'entité de l'utilisateur et l'heure actuelle.
		String fuseauHoraire = Optional.ofNullable(utilisateur)
									   .map(Utilisateur::getService)
									   .map(fr.gouv.diplomatie.rece.etatcivil.domain.habilitation.Service::getAdresseService)
									   .map(AdresseService::getFuseauHoraire)
									   .orElseThrow(() -> new BusinessException(BusinessExceptionCode.ADRESSE_SERVICE_NEXISTE_PAS, utilisateur.getService().getLibelleService()));

		LocalTime maintenant = Instant.now(clock).atZone(ZoneId.of(fuseauHoraire)).toLocalTime();

		if (maintenant.isAfter(heureDebutBlocage) && maintenant.isBefore(heureFinBlocage)) {
			throw new BusinessException(BusinessExceptionCode.ERREUR_PLAGE_HOTAIRE_SIGNATURE, heureDebutBlocageSignature, heureFinBlocageSignature);
		}
	}

	private DocumentMentions enregistrerDocumentMentions(UUID idActe) {
		return Optional.ofNullable(loadDocumentMentionsPort.getDocumentMentionsByIdActeAndStatut(idActe, StatutDocumentMentions.NON_SIGNE))
					   .orElseGet(() -> {
						   DocumentMentions documentMentions = new DocumentMentions();
						   documentMentions.setId(UuidCreator.getShortPrefixComb());
						   documentMentions.setIdActe(idActe);
						   documentMentions.setStatutDocumentMentions(StatutDocumentMentions.NON_SIGNE);
						   documentMentions.setNumeroOrdreSignature(Integer.valueOf(attributionProchainNumeroOrdreDocumentMention(idActe)));
						   return saveDocumentMentionsPort.enregistrer(documentMentions);
					   });
	}

	private void valoriserMentionsActe(Acte acteSigne, Utilisateur utilisateur, Signature signature) {
		String villeApposition = utilisateur.getService().getAdresseService().getVille();
		LocalDate dateApposition = DateUtils.getDateActuelleDuService(utilisateur);
		String texteApposition = RegleFormatageTexteActeEtExtraitEtabliNaissance.formatTexteAppositionMention(villeApposition, dateApposition);
		acteSigne.getMentions().stream().filter(mention -> StatutMention.BROUILLON.equals(mention.getStatut())).forEach(mention -> {
			mention.setVilleApposition(villeApposition);
			mention.setDateApposition(dateApposition);
			if (mention.getTextes() != null) {
				mention.getTextes().setTexteApposition(texteApposition);
			}
			if (mention.getAutoriteEtatCivil() != null) {
				mention.getAutoriteEtatCivil().setNomOEC(signature.getNomOEC());
				mention.getAutoriteEtatCivil().setPrenomOEC(signature.getPrenomOEC());
				mention.getTextes().setTexteOEC(RegleFormatageTexteActeEtExtraitEtabliNaissance.formatTexteOecMention(mention.getAutoriteEtatCivil()));
			}
			updateMentionPort.addMention(mention, acteSigne.getId());
		});
	}

	@Override
	public byte[] composerPdfMentionsUlterieures(Acte acte) {
		// Vérifier si l'acte est vide (ni image ni texte).
		if ((acte.getImages() == null || acte.getImages().isEmpty()) && (acte.getCorpsTexte() == null || acte.getCorpsTexte().getTexte() == null)) {
			throw new BusinessException(BusinessExceptionCode.ACTE_VIDE, acte.getId());
		}

		// Vérifier si l'acte est électronique.
		if (!acte.estActeElectronique()) {
			throw new BusinessException(BusinessExceptionCode.ACTE_NON_ELECTRONIQUE, acte.getId());
		}

		// Vérifier si l'acte contient des mentions à signer valides.
		boolean acteAvecDesMentionASigner = acte.getMentions().stream()
												.anyMatch(mention -> StatutMention.BROUILLON.equals(mention.getStatut())
														&& mention.getTextes() != null
														&& StringUtils.isNotEmpty(mention.getTextes().getTexteMention()));
		if (!acteAvecDesMentionASigner) {
			throw new BusinessException(BusinessExceptionCode.AUCUNE_MENTION_A_SIGNER, acte.getId());
		}

		// Composition des mentions ultérieures valides à signer.
		return compositionPort.composeDocumentsPdf(CompositionJsonUtils.composerJsonPourGenerationMentionsUlterieuresPdf(acte, attributionProchainNumeroOrdreDocumentMention(acte.getId())), TypeDocumentComposition.MENTIONS_ULTERIEURES);
	}

	@Override
	@Transactional
	public void integrerDocumentMentionSigne(UUID idActe, String idArobas, DocumentSigne documentSigne) {
		// Vérification du droit de l'utilisateur
		Utilisateur utilisateur = loadUtilisateursPort.getUtilisateurByIdArobas(idArobas);
		utilisateur.verifierDroit(NomDroit.SIGNER_MENTION);
		Acte acte = getActeSigneById(idActe, idArobas);
		DSSDocument documentSignePadesLT;
		// On vérifie que la signature est dispo pour ne pas griller de numéros d'actes
		if (suiviSignature.getStatutSignature() == StatutSignature.DISPONIBLE) {
			// Cet appel n'est pas dans le 'try' car on gère différement les cas d'erreurs techniques d'horodatage et les autres erreurs techniques :
			// - En cas d'erreur technique sur l'horodatage, l'horodatage est bloqué par la méthode  augmentationPdfSigneFromPadesBToPadesLT() mais avec possibilité
			// de déblocage automatique par le TI SuiviHorodatage dès que le service est de nouveau disponible.
			// - Dans les autres cas d'erreur technique, on bloque l'horodatage avec besoin d'analyse humaine.
			documentSignePadesLT = horodatageTraitement.augmentationPdfSigneFromPadesBToPadesLT(documentSigne);
		} else {
			throw new TechnicalException(TechnicalExceptionCode.SIGNATURE_INDISPO);
		}
		try {
			// Enregistrement et horodatage du document au statut non signé
			DocumentMentions documentMentions = loadDocumentMentionsPort.getDocumentMentionsByIdActeAndStatut(idActe, StatutDocumentMentions.NON_SIGNE);
			Pair<ResultatEnregistrerDocumentSwift, ResultatHorodatageDocumentSigne> resultat = enregistrerDocument(documentSignePadesLT, documentMentions.getId());

			// Récupération des IDs des mentions à mettre à jour
			List<UUID> idsMentions = acte.getMentions().stream()
										 .filter(mention -> StatutMention.BROUILLON == mention.getStatut())
										 .map(Mention::getId)
										 .toList();

			// Mise à jour des mentions liées au document
			majMentionsLieesAuDocument(idsMentions, resultat.getRight().getDateHorodatage(), documentMentions.getId());
			updateMentionPort.majMentionsApresSignature(idsMentions, resultat.getRight().getDateHorodatage(), documentMentions.getId());
			updateDocumentMentionsPort.updateDocumentMentionApresSignature(idActe, StatutDocumentMentions.SIGNE,
																		   resultat.getLeft().getConteneurSwift(),
																		   resultat.getLeft().getReferenceSwift());

			// Enregistrer la nouvelle situation de l'Analyse marginale
			List<UUID> idsAnalysesMarginalesNonValides = loadAnalyseMarginalePort.getIdsAnalysesMarginalesNonValidesByIdActe(idActe);
			if (idsAnalysesMarginalesNonValides.size() == 1) {
				updateAnalyseMarginalePort.updateSituationAnalyseMarginale(idActe, utilisateur.getNom(), utilisateur.getPrenom());
			} else if (idsAnalysesMarginalesNonValides.size() > 1) {
				throw new BusinessException(BusinessExceptionCode.ERREUR_PLUSIEURS_ANALYSES_MARGINALES_NON_VALIDE_SUR_ACTE, idActe, idsAnalysesMarginalesNonValides);
			}

			//Mise à jour de la table personne, autre_nom, prenom et autre_prenom
			AnalyseMarginale analyseMarginale = loadAnalyseMarginalePort.getDerniereAnalyseMarginaleSigneeByIdActe(idActe);
			List<Personne> personneTitulaires = acte.getPersonnes();
			personneTraitement.majPersonnesParAnalyseMarginale(personneTitulaires, analyseMarginale);

			// Enregistrer les preuves de signature
			preuveSignatureActeTraitement.enregistreApresSignatureDocumentMentions(documentSigne, acte, documentMentions, utilisateur, resultat.getLeft(), resultat.getRight().getDateHorodatage());
			updateActePort.updateDateDerniereMiseAJour(idActe, ZonedDateTime.now(ZoneId.of(utilisateur.getFuseauHoraire())).toLocalDate());

		} catch (DateTimeException dateTimeException) {
			throw new BusinessException(BusinessExceptionCode.FUSEAU_HORAIRE_INVALIDE, utilisateur.getFuseauHoraire(), dateTimeException);
		} catch (TechnicalException technicalException) {
			log.error(technicalException.getCode(), technicalException);
			suiviHorodatage.creerBlocageAAnalyser();
			throw technicalException;
		}
	}

	private Pair<ResultatEnregistrerDocumentSwift, ResultatHorodatageDocumentSigne> enregistrerDocument(DSSDocument documentSignePadesLT, UUID idActe) {
		ResultatHorodatageDocumentSigne resultatHorodatageDocumentSigne = horodatageTraitement.validationEtRecuperationDateHorodatage(documentSignePadesLT);
		ResultatEnregistrerDocumentSwift resultatEnregistrerDocumentSwift = stockageTraitement.enregistrerDocumentSwift(resultatHorodatageDocumentSigne.getContenuDocument(), SwiftUtils.getContainerNameMention(), idActe);
		return Pair.of(resultatEnregistrerDocumentSwift, resultatHorodatageDocumentSigne);
	}

	private void majMentionsLieesAuDocument(List<UUID> idsMentions, Instant dateHorodatage, UUID idDocumentMentions) {
		updateMentionPort.majMentionsApresSignature(idsMentions, dateHorodatage, idDocumentMentions);
	}

	private Acte getActeSigneById(UUID idActe, String idArobas) {
		// Vérification des droits
		Utilisateur utilisateur = loadUtilisateursPort.getUtilisateurByIdArobas(idArobas);
		VerificationDroits.controlerUtilisateur(utilisateur);
		// Récupération de l'acte signé
		Acte acte = loadActePort.getActeSigneById(idActe);
		if (acte == null) {
			throw new BusinessException(BusinessExceptionCode.AUCUN_ACTE_STATUT_SIGNE, idActe);
		}
		//Vérifier est ce que l'acte est un Projet Acte
		if (!acte.estActeSigne()) {
			throw new BusinessException(BusinessExceptionCode.STATUT_ACTE_SIGNE_INCOHERENT, idActe, acte.getStatut());
		}
		return acte;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void abandonMiseAJourMentionsActe(final UUID idActe, final String idArobas) {
		// Récupération de l'utilisateur et vérification du droit "Mettre à jour acte"
		final Utilisateur utilisateur = loadUtilisateursPort.getUtilisateurByIdArobas(idArobas);
		VerificationDroits.exceptionSiUtilisateurNaPasLeDroit(utilisateur, NomDroit.METTRE_A_JOUR_ACTE);

		// On supprime tout d'abord toute les mentions au statut brouillon qui ont un texte mention renseigné
		// et absence d'un texte de délivrance et de texte mention plurilingue
		supprimerMajMentionsCreation(loadMentionPort.getMentionsByIdActe(idActe));

		// On supprime les analyses marginales non valides s'il y en a
		analyseMarginaleTraitement.supprimerAnalysesMarginalesNonValides(idActe);
	}

	public void ajouterMentionsCreation(List<Mention> mentions, UUID idActe) {
		Long dernierNumeroOrdre = Optional.ofNullable(loadMentionPort.getDernierNumeroOrdreMentionsSigneeActeById(idActe)).
										  orElse(0L);

		mentions.forEach(mention -> {
			initialiserChampsMentionCreation(mention, dernierNumeroOrdre);
			updateMentionPort.addMention(mention, idActe);
		});
	}

	public void supprimerMajMentionsCreation(List<Mention> mentions) {
		// On supprime tout d'abord toute les mentions au statut brouillon qui ont un un texte mention renseigné
		// et absence d'un texte de délivrance et de texte mention plurilingue
		final List<UUID> listeSupprMentions = mentions.stream()
													  .filter(this::estMentionASupprimer)
													  .map(Mention::getId)
													  .collect(Collectors.toList());

		deleteMentionPort.deleteMentions(listeSupprMentions);
	}

	private boolean estMentionASupprimer(Mention mention) {
		return mention.getTextes().getTexteMention() != null
				&& mention.getStatut() == StatutMention.BROUILLON
				&& mention.getTextes().getTexteMentionDelivrance() == null
				&& mention.getTextes().getTexteMentionPlurilingue() == null
				&& mention.getOrigine() == TypeOrigine.RECE;
	}

	private void initialiserChampsMentionCreation(Mention mention, Long dernierNumeroOrdre) {
		mention.setNumeroOrdre(dernierNumeroOrdre + mention.getNumeroOrdre());
		mention.setVilleApposition(null);
		mention.setRegionApposition(null);
		mention.setDateApposition(null);
		mention.setDateCreation(null);
		mention.setNumeroOrdreExtrait(null);
		mention.getTextes().setTexteMention(generationPointFinTexteMention(mention.getTextes().getTexteMention()));
		mention.getTextes().setTexteApposition(null);
		mention.getTextes().setTexteOEC(null);
		mention.getTextes().setTexteMentionPlurilingue(null);
		mention.getTextes().setTexteMentionDelivrance(null);
		if (mention.getAutoriteEtatCivil() == null) {
			mention.setAutoriteEtatCivil(new AutoriteEtatCivil());
		}
		mention.getAutoriteEtatCivil().setPrenomOEC(null);
		mention.getAutoriteEtatCivil().setNomOEC(null);
		mention.setOrigine(TypeOrigine.RECE);
	}

	private String generationPointFinTexteMention(String texteMention) {
		String mentionNettoyer = texteMention.trim();

		if (!Objects.equals(mentionNettoyer.substring(mentionNettoyer.length() - 1), ".") &&
				!Objects.equals(mentionNettoyer.substring(mentionNettoyer.length() - 1), ")")) {
			mentionNettoyer += ".";
		}

		return StringUtils.capitalize(mentionNettoyer);
	}

	public String getCorpsNouvellesNentions(List<Mention> mentionsASigner) {
		StringBuilder corpsNouvellesNentions = new StringBuilder();

		mentionsASigner.stream()
					   .sorted(Comparator.comparingLong(Mention::getNumeroOrdre))
					   .forEach(mention -> corpsNouvellesNentions.append(mention.getTextes().getTexteMention())
																 .append(SPACE)
																 .append(mention.getTextes().getTexteApposition())
																 .append(SPACE)
																 .append(mention.getTextes().getTexteOEC())
																 .append(LF));

		return corpsNouvellesNentions.toString();
	}

	public Byte attributionProchainNumeroOrdreDocumentMention(UUID idActe) {
		return (byte) ((loadMentionPort.getDernierNumeroOrdreSignatureDocumentMentionsActeById(idActe) == null ? 0 : loadMentionPort.getDernierNumeroOrdreSignatureDocumentMentionsActeById(idActe)) + 1);
	}
}
