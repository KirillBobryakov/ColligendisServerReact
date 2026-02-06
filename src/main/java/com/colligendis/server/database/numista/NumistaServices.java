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
import com.colligendis.server.database.numista.service.RulerGroupService;
import com.colligendis.server.database.numista.service.RulerService;
import com.colligendis.server.database.numista.service.SubjectService;

@Service
public class NumistaServices {

	public final NTypeService nTypeService;
	public final CollectibleTypeService collectibleTypeService;
	public final IssuerService issuerService;
	public final SectionService sectionService;
	public final SubjectService subjectService;
	public final CountryService countryService;
	public final RulerService rulerService;
	public final RulerGroupService rulerGroupService;
	public final CurrencyService currencyService;
	public final IssuingEntityService issuingEntityService;
	public final DenominationService denominationService;
	public final CommemoratedEventService commemoratedEventService;
	public final SeriesService seriesService;
	public final CatalogueService catalogueService;
	public final CatalogueReferenceService catalogueReferenceService;
	public final AuthorService authorService;
	public final ColligendisUserService colligendisUserService;

	private NumistaServices(NTypeService nTypeService, CollectibleTypeService collectibleTypeService,
			IssuerService issuerService, SectionService sectionService, SubjectService subjectService,
			CountryService countryService, RulerService rulerService,
			RulerGroupService rulerGroupService, CurrencyService currencyService,
			IssuingEntityService issuingEntityService, DenominationService denominationService,
			CommemoratedEventService commemoratedEventService, SeriesService seriesService,
			CatalogueService catalogueService, CatalogueReferenceService catalogueReferenceService,
			AuthorService authorService, ColligendisUserService colligendisUserService) {
		this.nTypeService = nTypeService;
		this.collectibleTypeService = collectibleTypeService;
		this.issuerService = issuerService;
		this.sectionService = sectionService;
		this.subjectService = subjectService;
		this.countryService = countryService;
		this.rulerService = rulerService;
		this.rulerGroupService = rulerGroupService;
		this.currencyService = currencyService;
		this.issuingEntityService = issuingEntityService;
		this.denominationService = denominationService;
		this.commemoratedEventService = commemoratedEventService;
		this.seriesService = seriesService;
		this.catalogueService = catalogueService;
		this.catalogueReferenceService = catalogueReferenceService;
		this.authorService = authorService;
		this.colligendisUserService = colligendisUserService;
	}

}
