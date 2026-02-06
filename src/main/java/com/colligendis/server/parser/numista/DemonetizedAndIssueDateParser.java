package com.colligendis.server.parser.numista;

import java.util.function.Function;

import org.jsoup.nodes.Element;

import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.numista.service.NTypeService;

import reactor.core.publisher.Mono;

public class DemonetizedAndIssueDateParser {

	public static final DemonetizedAndIssueDateParser instance = new DemonetizedAndIssueDateParser();

	private NTypeService nTypeService;

	private NTypeService getNTypeService() {
		if (nTypeService == null) {
			nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
		}
		return nTypeService;
	}

	public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono
			.defer(() -> parseDemonetizedAndIssueDate(page));

	private Mono<NumistaPage> parseDemonetizedAndIssueDate(NumistaPage numistaPage) {
		if (numistaPage.nType == null) {
			return Mono.just(numistaPage);
		}

		String yearIssueDate = NumistaParseUtils.getAttribute(
				numistaPage.page.selectFirst("input[name=year_issue_date]"),
				"value");
		String monthIssueDate = NumistaParseUtils.getAttribute(
				numistaPage.page.selectFirst("input[name=month_issue_date]"),
				"value");
		String dayIssueDate = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("input[name=day_issue_date]"),
				"value");

		Element demonetisationYesElement = numistaPage.page
				.selectFirst("input[type=radio][name=demonetisation][value=oui]");
		Element demonetisationNoElement = numistaPage.page
				.selectFirst("input[type=radio][name=demonetisation][value=non]");
		Element demonetisationUnknownElement = numistaPage.page
				.selectFirst("input[type=radio][name=demonetisation][value=inconnu]");

		String demonetized = null;
		String demonetizationYear = null;
		String demonetizationMonth = null;
		String demonetizationDay = null;

		if (demonetisationNoElement != null && demonetisationNoElement.attributes().hasKey("checked")) {
			demonetized = "0";
		} else if (demonetisationUnknownElement != null
				&& demonetisationUnknownElement.attributes().hasKey("checked")) {
			demonetized = "2";

		} else if (demonetisationYesElement != null && demonetisationYesElement.attributes().hasKey("checked")) {
			demonetized = "1";
			demonetizationYear = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("#ad"), "value");
			demonetizationMonth = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("#md"), "value");
			demonetizationDay = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("#jd"), "value");
		}
		numistaPage.nType.setDemonetized(demonetized);
		numistaPage.nType.setDemonetizationYear(demonetizationYear);
		numistaPage.nType.setDemonetizationMonth(demonetizationMonth);
		numistaPage.nType.setDemonetizationDay(demonetizationDay);
		numistaPage.nType.setYearIssueDate(yearIssueDate);
		numistaPage.nType.setMonthIssueDate(monthIssueDate);
		numistaPage.nType.setDayIssueDate(dayIssueDate);

		return getNTypeService().update(numistaPage.nType, numistaPage.colligendisUserM).flatMap(
				result -> result.fold(
						err -> {
							return Mono.just(numistaPage);
						},
						updated -> {
							return Mono.just(numistaPage);
						}));

	}

}
