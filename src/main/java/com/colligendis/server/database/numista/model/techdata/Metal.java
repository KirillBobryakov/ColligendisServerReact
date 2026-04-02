package com.colligendis.server.database.numista.model.techdata;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class Metal extends AbstractNode {
	public static final String LABEL = "METAL";

	private String nid;
	private String name;

	// <option value="">Unknown</option>
	// <option value="38">Acmonital</option>
	// <option value="45">Aluminium</option>
	// <option value="50">Aluminium brass</option>
	// <option value="10">Aluminium bronze</option>
	// <option value="30">Aluminium-nickel-bronze</option>
	// <option value="54">Aluminium-zinc-bronze</option>
	// <option value="34">Bakelite</option>
	// <option value="7">Billon</option>
	// <option value="4">Brass</option>
	// <option value="5">Bronze</option>
	// <option value="65">Bronze-nickel</option>
	// <option value="37">Bronzital</option>
	// <option value="24">Cardboard</option>
	// <option value="63">Ceramic</option>
	// <option value="41">Chromium</option>
	// <option value="39">Clay composite</option>
	// <option value="3">Copper</option>
	// <option value="32">Copper-aluminium</option>
	// <option value="46">Copper-aluminium-nickel</option>
	// <option value="2">Copper-nickel</option>
	// <option value="36">Copper-nickel-iron</option>
	// <option value="17">Electrum</option>
	// <option value="60">Fiber</option>
	// <option value="59">Florentine bronze</option>
	// <option value="48">Gilding metal</option>
	// <option value="64">Glass</option>
	// <option value="6">Gold</option>
	// <option value="74">Iridium</option>
	// <option value="13">Iron</option>
	// <option value="21" selected="selected">Lead</option>
	// <option value="75">Leaded bronze</option>
	// <option value="62">Leaded copper</option>
	// <option value="56">Magnesium</option>
	// <option value="28">Manganese brass</option>
	// <option value="8">Nickel</option>
	// <option value="16">Nickel brass</option>
	// <option value="12">Nickel silver</option>
	// <option value="53">Nickel-steel</option>
	// <option value="55">Nickel-zinc</option>
	// <option value="49">Niobium</option>
	// <option value="18">Nordic gold</option>
	// <option value="52">Orichalcum</option>
	// <option value="72">Other</option>
	// <option value="44">Palladium</option>
	// <option value="25">Pewter</option>
	// <option value="14">Plastic</option>
	// <option value="22">Platinum</option>
	// <option value="26">Porcelain</option>
	// <option value="33">Potin</option>
	// <option value="43">Resin</option>
	// <option value="70">Rhodium</option>
	// <option value="71">Ruthenium</option>
	// <option value="1">Silver</option>
	// <option value="15">Stainless steel</option>
	// <option value="9">Steel</option>
	// <option value="40">Tantalum</option>
	// <option value="19">Tin</option>
	// <option value="58">Tin brass</option>
	// <option value="57">Tin-lead</option>
	// <option value="61">Tin-zinc</option>
	// <option value="35">Titanium</option>
	// <option value="23">Tombac</option>
	// <option value="47">Virenium</option>
	// <option value="20">Wood</option>
	// <option value="27">Zamak</option>
	// <option value="11">Zinc</option>
	// <option value="42">Zinc-aluminium</option>
}
