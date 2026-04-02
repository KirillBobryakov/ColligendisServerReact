package com.colligendis.server.database.numista;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.service.SectionService;
import com.colligendis.server.database.numista.service.SeriesService;
import com.colligendis.server.database.numista.service.AuthorService;
import com.colligendis.server.database.numista.service.CatalogueReferenceService;
import com.colligendis.server.database.numista.service.CatalogueService;
import com.colligendis.server.database.numista.service.CollectibleTypeService;
import com.colligendis.server.database.numista.service.CommemoratedEventService;
import com.colligendis.server.database.numista.service.CountryService;
import com.colligendis.server.database.numista.service.CurrencyService;
import com.colligendis.server.database.numista.service.DenominationService;
import com.colligendis.server.database.numista.service.IssuerService;
import com.colligendis.server.database.numista.service.IssuingEntityService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.RulingAuthorityGroupService;
import com.colligendis.server.database.numista.service.RulingAuthorityService;
import com.colligendis.server.database.numista.service.SubjectService;
import com.colligendis.server.database.numista.service.techdata.CompositionService;
import com.colligendis.server.database.numista.service.techdata.CompositionTypeService;
import com.colligendis.server.database.numista.service.techdata.MetalService;
import com.colligendis.server.database.numista.service.techdata.ShapeService;
import com.colligendis.server.database.numista.service.techdata.TechniqueService;

@Service
public class NumistaServices {

	public final NTypeService nTypeService;
	public final CollectibleTypeService collectibleTypeService;
	public final IssuerService issuerService;
	public final SectionService sectionService;
	public final SubjectService subjectService;
	public final CountryService countryService;
	public final RulingAuthorityService rulingAuthorityService;
	public final RulingAuthorityGroupService rulingAuthorityGroupService;
	public final CurrencyService currencyService;
	public final IssuingEntityService issuingEntityService;
	public final DenominationService denominationService;
	public final CommemoratedEventService commemoratedEventService;
	public final SeriesService seriesService;
	public final CatalogueService catalogueService;
	public final CatalogueReferenceService catalogueReferenceService;
	public final CompositionService compositionService;
	public final CompositionTypeService compositionTypeService;
	public final MetalService metalService;
	public final ShapeService shapeService;
	public final TechniqueService techniqueService;
	public final AuthorService authorService;
	public final ColligendisUserService colligendisUserService;

	private NumistaServices(NTypeService nTypeService, CollectibleTypeService collectibleTypeService,
			IssuerService issuerService, SectionService sectionService, SubjectService subjectService,
			CountryService countryService, RulingAuthorityService rulingAuthorityService,
			RulingAuthorityGroupService rulingAuthorityGroupService, CurrencyService currencyService,
			IssuingEntityService issuingEntityService, DenominationService denominationService,
			CommemoratedEventService commemoratedEventService, SeriesService seriesService,
			CatalogueService catalogueService, CatalogueReferenceService catalogueReferenceService,
			CompositionService compositionService, CompositionTypeService compositionTypeService,
			MetalService metalService, ShapeService shapeService, TechniqueService techniqueService,
			AuthorService authorService,
			ColligendisUserService colligendisUserService) {
		this.nTypeService = nTypeService;
		this.collectibleTypeService = collectibleTypeService;
		this.issuerService = issuerService;
		this.sectionService = sectionService;
		this.subjectService = subjectService;
		this.countryService = countryService;
		this.rulingAuthorityService = rulingAuthorityService;
		this.rulingAuthorityGroupService = rulingAuthorityGroupService;
		this.currencyService = currencyService;
		this.issuingEntityService = issuingEntityService;
		this.denominationService = denominationService;
		this.commemoratedEventService = commemoratedEventService;
		this.seriesService = seriesService;
		this.catalogueService = catalogueService;
		this.catalogueReferenceService = catalogueReferenceService;
		this.compositionService = compositionService;
		this.compositionTypeService = compositionTypeService;
		this.metalService = metalService;
		this.shapeService = shapeService;
		this.techniqueService = techniqueService;
		this.authorService = authorService;
		this.colligendisUserService = colligendisUserService;
	}

}
