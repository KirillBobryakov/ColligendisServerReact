package com.colligendis.server.parser.numista.collection;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.util.StringUtils;

/**
 * Values for Numista {@code collec_form_grading_designation} / {@code gradingDesignation[]}.
 * Options depend on {@link NumistaGradingService} ({@link #serviceId}).
 */
public enum NumistaGradingDesignation {
	PCGS_RD("100", "3", "RD"),
	PCGS_RB("101", "3", "RB"),
	PCGS_BN("102", "3", "BN"),
	PCGS_CAM("103", "3", "CAM"),
	PCGS_DCAM("104", "3", "DCAM"),
	PCGS_SP("105", "3", "SP"),
	PMG_DESIGNATION("106", "4", "★"),
	PMG_EPQ("107", "4", "EPQ"),
	PMG_NET("108", "4", "NET"),
	NGC_ANCIENT_DESIGNATION("192", "2", "★"),
	NGC_ANCIENT_FINE_STYLE("193", "2", "fine style"),
	NGC_ANCIENT_MARKS("194", "2", "marks"),
	CAC_DESIGNATION("218", "5", "+"),
	CAC_RD("219", "5", "RD"),
	CAC_QR("220", "5", "QR"),
	CAC_CA("221", "5", "CA"),
	CAC_CAM("222", "5", "CAM"),
	NGC_D_5FS_DPL("295", "1", "5FS DPL"),
	NGC_D_5FS("296", "1", "5FS"),
	NGC_D_5FS_PL("297", "1", "5FS PL"),
	NGC_D_6FS_DPL("298", "1", "6FS DPL"),
	NGC_D_6FS("299", "1", "6FS"),
	NGC_D_6FS_PL("300", "1", "6FS PL"),
	NGC_BN("301", "1", "BN"),
	NGC_BN_BRILLIANT("302", "1", "BN BRILLIANT"),
	NGC_BN_CAMEO("303", "1", "BN CAMEO"),
	NGC_BN_DPL("304", "1", "BN DPL"),
	NGC_BN_MATTE("305", "1", "BN MATTE"),
	NGC_BN_PL("306", "1", "BN PL"),
	NGC_BN_SATIN("307", "1", "BN SATIN"),
	NGC_BN_ULTRA_CAMEO("308", "1", "BN ULTRA CAMEO"),
	NGC_CAMEO("309", "1", "CAMEO"),
	NGC_DPL_FIRST_DAY_OF_MINTAGE("310", "1", "DPL FIRST DAY OF MINTAGE"),
	NGC_DPL_MINT_ERROR("311", "1", "DPL MINT ERROR"),
	NGC_DPL("312", "1", "DPL"),
	NGC_ENHANCED_FINISH("313", "1", "ENHANCED FINISH"),
	NGC_DPL_ENHANCED_FINISH("314", "1", "DPL ENHANCED FINISH"),
	NGC_PL_ENHANCED_FINISH("315", "1", "PL ENHANCED FINISH"),
	NGC_ENHANCED_REV_PF("316", "1", "ENHANCED REV PF"),
	NGC_UC_ENHANCED_FINISH("317", "1", "UC ENHANCED FINISH"),
	NGC_FB("318", "1", "FB"),
	NGC_FB_DPL("319", "1", "FB DPL"),
	NGC_FBL("320", "1", "FBL"),
	NGC_FB_PL("321", "1", "FB PL"),
	NGC_FIRST_DAY_OF_ISSUE("322", "1", "FIRST DAY OF ISSUE"),
	NGC_DPL_FIRST_DAY_OF_ISSUE("323", "1", "DPL FIRST DAY OF ISSUE"),
	NGC_PL_FIRST_DAY_OF_ISSUE("324", "1", "PL FIRST DAY OF ISSUE"),
	NGC_FH("325", "1", "FH"),
	NGC_FH_DPL("326", "1", "FH DPL"),
	NGC_FH_PL("327", "1", "FH PL"),
	NGC_FBL_DPL("328", "1", "FBL DPL"),
	NGC_FBL_PL("329", "1", "FBL PL"),
	NGC_FIRST_DAY_OF_MINTAGE("330", "1", "FIRST DAY OF MINTAGE"),
	NGC_FT("331", "1", "FT"),
	NGC_FT_DPL("332", "1", "FT DPL"),
	NGC_FT_PL("333", "1", "FT PL"),
	NGC_MATTE("334", "1", "MATTE"),
	NGC_MINT_ERROR("335", "1", "MINT ERROR"),
	NGC_PL_FIRST_DAY_OF_MINTAGE("336", "1", "PL FIRST DAY OF MINTAGE"),
	NGC_PL("337", "1", "PL"),
	NGC_PL_MINT_ERROR("338", "1", "PL MINT ERROR"),
	NGC_RB("339", "1", "RB"),
	NGC_RB_BRILLIANT("340", "1", "RB BRILLIANT"),
	NGC_RB_CAMEO("341", "1", "RB CAMEO"),
	NGC_RB_DPL("342", "1", "RB DPL"),
	NGC_RB_MATTE("343", "1", "RB MATTE"),
	NGC_RB_PL("344", "1", "RB PL"),
	NGC_RB_SATIN("345", "1", "RB SATIN"),
	NGC_RB_ULTRA_CAMEO("346", "1", "RB ULTRA CAMEO"),
	NGC_RD("347", "1", "RD"),
	NGC_RD_BRILLIANT("348", "1", "RD BRILLIANT"),
	NGC_RD_CAMEO("349", "1", "RD CAMEO"),
	NGC_RD_DPL("350", "1", "RD DPL"),
	NGC_RD_MATTE("351", "1", "RD MATTE"),
	NGC_RD_PL("352", "1", "RD PL"),
	NGC_RD_SATIN("353", "1", "RD SATIN"),
	NGC_RD_ULTRA_CAMEO("354", "1", "RD ULTRA CAMEO"),
	NGC_RD_FIRST_DAY_OF_ISSUE("355", "1", "RD FIRST DAY OF ISSUE"),
	NGC_RELEASE_CEREMONY("356", "1", "RELEASE CEREMONY"),
	NGC_RD_RELEASE_CEREMONY("357", "1", "RD RELEASE CEREMONY"),
	NGC_SATIN("358", "1", "SATIN"),
	NGC_ULTRA_CAMEO("359", "1", "ULTRA CAMEO"),
	CAC_BN("360", "5", "BN"),
	CAC_RB("361", "5", "RB"),
	CAC_DCAM("362", "5", "DCAM"),
	CAC_PL("363", "5", "PL"),
	CAC_DMPL("364", "5", "DMPL"),
	CAC_FS("365", "5", "FS"),
	CAC_FB("366", "5", "FB"),
	CAC_FH("367", "5", "FH"),
	CAC_FBL("368", "5", "FBL"),
	CAC_FIRST_DELIVERY("369", "5", "First Delivery"),
	CAC_FIRST_DAY_OF_DELIVERY("370", "5", "First Day of Delivery"),
	CAC_ADVANCED_DELIVERY("371", "5", "Advanced Delivery"),
	NGC_DESIGNATION("419", "1", "+"),
	NGC_DESIGNATION_V420("420", "1", "★"),
	PCGS_PL("421", "3", "PL"),
	PCGS_DM("422", "3", "DM"),
	PCGS_MINT_ERROR("435", "3", "Mint Error"),
	CAC_MINT_ERROR("436", "5", "Mint Error"),
	PCGS_DESIGNATION("437", "3", "+"),
	PCGS_BANKNOTE_PPQ("466", "6", "PPQ"),
	PCGS_BANKNOTE_OPQ("467", "6", "OPQ"),
	PCGS_BANKNOTE_DETAILS("470", "6", "DETAILS"),
	PCGS_BANKNOTE_FIRSTPRINT("471", "6", "FirstPrint"),
	PCGS_BANKNOTE_FIRST_DAY_OF_ISSUE("472", "6", "First Day of Issue"),
	NGCX_D_5FS_DPL("546", "7", "5FS DPL"),
	NGCX_D_5FS("547", "7", "5FS"),
	NGCX_D_5FS_PL("548", "7", "5FS PL"),
	NGCX_D_6FS_DPL("549", "7", "6FS DPL"),
	NGCX_D_6FS("550", "7", "6FS"),
	NGCX_D_6FS_PL("551", "7", "6FS PL"),
	NGCX_BN("552", "7", "BN"),
	NGCX_BN_BRILLIANT("553", "7", "BN BRILLIANT"),
	NGCX_BN_CAMEO("554", "7", "BN CAMEO"),
	NGCX_BN_DPL("555", "7", "BN DPL"),
	NGCX_BN_MATTE("556", "7", "BN MATTE"),
	NGCX_BN_PL("557", "7", "BN PL"),
	NGCX_BN_SATIN("558", "7", "BN SATIN"),
	NGCX_BN_ULTRA_CAMEO("559", "7", "BN ULTRA CAMEO"),
	NGCX_CAMEO("560", "7", "CAMEO"),
	NGCX_DPL_FIRST_DAY_OF_MINTAGE("561", "7", "DPL FIRST DAY OF MINTAGE"),
	NGCX_DPL_MINT_ERROR("562", "7", "DPL MINT ERROR"),
	NGCX_DPL("563", "7", "DPL"),
	NGCX_ENHANCED_FINISH("564", "7", "ENHANCED FINISH"),
	NGCX_DPL_ENHANCED_FINISH("565", "7", "DPL ENHANCED FINISH"),
	NGCX_PL_ENHANCED_FINISH("566", "7", "PL ENHANCED FINISH"),
	NGCX_ENHANCED_REV_PF("567", "7", "ENHANCED REV PF"),
	NGCX_UC_ENHANCED_FINISH("568", "7", "UC ENHANCED FINISH"),
	NGCX_FB("569", "7", "FB"),
	NGCX_FB_DPL("570", "7", "FB DPL"),
	NGCX_FBL("571", "7", "FBL"),
	NGCX_FB_PL("572", "7", "FB PL"),
	NGCX_FIRST_DAY_OF_ISSUE("573", "7", "FIRST DAY OF ISSUE"),
	NGCX_DPL_FIRST_DAY_OF_ISSUE("574", "7", "DPL FIRST DAY OF ISSUE"),
	NGCX_PL_FIRST_DAY_OF_ISSUE("575", "7", "PL FIRST DAY OF ISSUE"),
	NGCX_FH("576", "7", "FH"),
	NGCX_FH_DPL("577", "7", "FH DPL"),
	NGCX_FH_PL("578", "7", "FH PL"),
	NGCX_FBL_DPL("579", "7", "FBL DPL"),
	NGCX_FBL_PL("580", "7", "FBL PL"),
	NGCX_FIRST_DAY_OF_MINTAGE("581", "7", "FIRST DAY OF MINTAGE"),
	NGCX_FT("582", "7", "FT"),
	NGCX_FT_DPL("583", "7", "FT DPL"),
	NGCX_FT_PL("584", "7", "FT PL"),
	NGCX_MATTE("585", "7", "MATTE"),
	NGCX_MINT_ERROR("586", "7", "MINT ERROR"),
	NGCX_PL_FIRST_DAY_OF_MINTAGE("587", "7", "PL FIRST DAY OF MINTAGE"),
	NGCX_PL("588", "7", "PL"),
	NGCX_PL_MINT_ERROR("589", "7", "PL MINT ERROR"),
	NGCX_RB("590", "7", "RB"),
	NGCX_RB_BRILLIANT("591", "7", "RB BRILLIANT"),
	NGCX_RB_CAMEO("592", "7", "RB CAMEO"),
	NGCX_RB_DPL("593", "7", "RB DPL"),
	NGCX_RB_MATTE("594", "7", "RB MATTE"),
	NGCX_RB_PL("595", "7", "RB PL"),
	NGCX_RB_SATIN("596", "7", "RB SATIN"),
	NGCX_RB_ULTRA_CAMEO("597", "7", "RB ULTRA CAMEO"),
	NGCX_RD("598", "7", "RD"),
	NGCX_RD_BRILLIANT("599", "7", "RD BRILLIANT"),
	NGCX_RD_CAMEO("600", "7", "RD CAMEO"),
	NGCX_RD_DPL("601", "7", "RD DPL"),
	NGCX_RD_MATTE("602", "7", "RD MATTE"),
	NGCX_RD_PL("603", "7", "RD PL"),
	NGCX_RD_SATIN("604", "7", "RD SATIN"),
	NGCX_RD_ULTRA_CAMEO("605", "7", "RD ULTRA CAMEO"),
	NGCX_RD_FIRST_DAY_OF_ISSUE("606", "7", "RD FIRST DAY OF ISSUE"),
	NGCX_RELEASE_CEREMONY("607", "7", "RELEASE CEREMONY"),
	NGCX_RD_RELEASE_CEREMONY("608", "7", "RD RELEASE CEREMONY"),
	NGCX_SATIN("609", "7", "SATIN"),
	NGCX_ULTRA_CAMEO("610", "7", "ULTRA CAMEO"),
	NGCX_DESIGNATION("631", "7", "+"),
	NGCX_DESIGNATION_V632("632", "7", "★"),
	PCGS_FS("761", "3", "FS"),
	PCGS_FB("762", "3", "FB"),
	PCGS_FH("763", "3", "FH"),
	PCGS_FBL("764", "3", "FBL"),
	PCGS_DMPL("765", "3", "DMPL"),
	PCGS_BM("766", "3", "BM"),
	PCGS_BMCA("767", "3", "BMCA"),
	PCGS_FIRST_STRIKE("768", "3", "First Strike"),
	PCGS_SATIN_FINISH("769", "3", "Satin Finish"),
	PCGS_SMS("770", "3", "SMS"),
	ANACS_DETAILS("814", "8", "DETAILS"),
	ANACS_CAMEO("844", "8", "CAMEO"),
	ANACS_DMPL("845", "8", "DMPL"),
	ANACS_D_5_STEPS("846", "8", "5 STEPS"),
	ANACS_D_5_5_STEPS("847", "8", "5.5 STEPS"),
	ANACS_D_6_STEPS("848", "8", "6 STEPS"),
	ANACS_FH("849", "8", "FH"),
	ANACS_FBL("850", "8", "FBL"),
	ANACS_DCAM("851", "8", "DCAM"),
	ANACS_RED("853", "8", "RED"),
	ANACS_BRN("854", "8", "BRN"),
	ANACS_RB("855", "8", "RB"),
	ANACS_PL("856", "8", "PL"),
	ANACS_FSB("857", "8", "FSB"),
	ANACS_UDM("858", "8", "UDM"),
	GENI_CLEANING("916", "9", "Cleaning"),
	GENI_DAMAGE("917", "9", "Damage"),
	GENI_SCRATCH("918", "9", "Scratch"),
	GENI_ENVT_DAMAGE("919", "9", "Envt Damage"),
	GENI_DESIGNATION("920", "9", "+"),
	GENI_GRAFFITI("931", "9", "Graffiti"),
	GENI_FILED_RIMS("938", "9", "Filed rims"),
	ICG_PL("939", "10", "PL"),
	ICG_CAM("940", "10", "CAM"),
	ICG_DCAM("941", "10", "DCAM"),
	ICG_DETAILS("942", "10", "Details"),
	ICG_RD("943", "10", "RD"),
	ICG_RB("944", "10", "RB"),
	ICG_BN("945", "10", "BN");

	private final String numistaValue;
	private final String serviceId;
	private final String label;

	NumistaGradingDesignation(String numistaValue, String serviceId, String label) {
		this.numistaValue = numistaValue;
		this.serviceId = serviceId;
		this.label = label;
	}

	public String getNumistaValue() {
		return numistaValue;
	}

	/** {@link NumistaGradingService} numista value. */
	public String getServiceId() {
		return serviceId;
	}

	public String getLabel() {
		return label;
	}

	public Optional<NumistaGradingService> getGradingService() {
		return NumistaGradingService.fromNumistaValue(serviceId);
	}

	public static Optional<NumistaGradingDesignation> fromNumistaValue(String value) {
		if (!StringUtils.hasText(value)) {
			return Optional.empty();
		}
		String trimmed = value.trim();
		return Arrays.stream(values())
				.filter(d -> d.numistaValue.equals(trimmed))
				.findFirst();
	}

	public static Optional<NumistaGradingDesignation> fromNumistaValueAndService(
			String value, NumistaGradingService gradingService) {
		if (!StringUtils.hasText(value) || gradingService == null) {
			return Optional.empty();
		}
		String trimmed = value.trim();
		String serviceValue = gradingService.getNumistaValue();
		return Arrays.stream(values())
				.filter(d -> d.numistaValue.equals(trimmed) && d.serviceId.equals(serviceValue))
				.findFirst();
	}

	public static List<NumistaGradingDesignation> forService(NumistaGradingService gradingService) {
		if (gradingService == null) {
			return List.of();
		}
		String serviceValue = gradingService.getNumistaValue();
		return Arrays.stream(values())
				.filter(d -> d.serviceId.equals(serviceValue))
				.toList();
	}

	public static Optional<NumistaGradingDesignation> fromLabelAndService(
			String label, NumistaGradingService gradingService) {
		if (!StringUtils.hasText(label) || gradingService == null) {
			return Optional.empty();
		}
		String trimmed = label.trim();
		String serviceValue = gradingService.getNumistaValue();
		return Arrays.stream(values())
				.filter(d -> d.serviceId.equals(serviceValue) && d.label.equalsIgnoreCase(trimmed))
				.findFirst();
	}
}
