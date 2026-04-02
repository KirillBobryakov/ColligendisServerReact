package com.colligendis.server.database.numista.model.techdata;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Shape extends AbstractNode {
	public static final String LABEL = "SHAPE";

	private String nid;
	private String name;

	// <option value="">Unknown</option>
	// <option value="51">Annular sector</option>
	// <option value="49">Cob</option>
	// <option value="35">Concave</option>
	// <option value="10">Decagonal (10-sided)</option>
	// <option value="12">Dodecagonal (12-sided)</option>
	// <option value="47">Equilateral curve heptagon (7-sided)</option>
	// <option value="62">Half circle</option>
	// <option value="53">Heart</option>
	// <option value="11">Hendecagonal (11-sided)</option>
	// <option value="7">Heptagonal (7-sided)</option>
	// <option value="57">Hexadecagonal (16-sided)</option>
	// <option value="6">Hexagonal (6-sided)</option>
	// <option value="58">Icosagonal (20-sided)</option>
	// <option value="59">Icosidigonal (22-sided)</option>
	// <option value="66">Icosihenagonal (21-sided)</option>
	// <option value="65">Icosipentagonal (25-sided)</option>
	// <option value="56">Icositetragonal (24-sided)</option>
	// <option value="45">Irregular</option>
	// <option value="42">Klippe</option>
	// <option value="72">Knife</option>
	// <option value="9">Nonagonal (9-sided)</option>
	// <option value="8">Octagonal (8-sided)</option>
	// <option value="48">Octagonal (8-sided) with a hole</option>
	// <option value="68">Octodecagonal (18-sided)</option>
	// <option value="50">Other</option>
	// <option value="36">Oval</option>
	// <option value="37">Oval with a loop</option>
	// <option value="60">Pentadecagonal (15-sided)</option>
	// <option value="5">Pentagonal (5-sided)</option>
	// <option value="63">Quarter circle</option>
	// <option value="4">Rectangular</option>
	// <option value="46">Rectangular (irregular)</option>
	// <option value="43">Reuleaux triangle</option>
	// <option value="64">Rhombus</option>
	// <option value="1">Round</option>
	// <option value="2">Round (irregular)</option>
	// <option value="34">Round with 4 pinches</option>
	// <option value="33">Round with a loop</option>
	// <option value="31">Round with a round hole</option>
	// <option value="32">Round with a square hole</option>
	// <option value="38">Round with cutouts</option>
	// <option value="39">Round with groove(s)</option>
	// <option value="15">Scalloped</option>
	// <option value="20">Scalloped (with 10 notches)</option>
	// <option value="21">Scalloped (with 11 notches)</option>
	// <option value="22">Scalloped (with 12 notches)</option>
	// <option value="23">Scalloped (with 13 notches)</option>
	// <option value="24">Scalloped (with 14 notches)</option>
	// <option value="25">Scalloped (with 15 notches)</option>
	// <option value="26">Scalloped (with 16 notches)</option>
	// <option value="27">Scalloped (with 17 notches)</option>
	// <option value="29">Scalloped (with 20 notches)</option>
	// <option value="14">Scalloped (with 4 notches)</option>
	// <option value="18">Scalloped (with 8 notches)</option>
	// <option value="30">Scalloped with a hole</option>
	// <option value="75">Sculptural</option>
	// <option value="54">Spade</option>
	// <option value="73">Spanish flower</option>
	// <option value="44">Square</option>
	// <option value="41">Square (irregular)</option>
	// <option value="55">Square with angled corners</option>
	// <option value="40">Square with rounded corners</option>
	// <option value="71">Square with scalloped edges</option>
	// <option value="74">Tetradecagonal (14-sided)</option>
	// <option value="3">Triangular</option>
	// <option value="13">Tridecagonal (13-sided)</option>

	// <option value="150">Other</option>
	// <option value ="100">Rectangular</option>
	// <option value="102">Rectangular (hand cut)</option>
	// <option value="101">Rectangular with undulating edge</option>
	// <option value="103">Round</option>
	// <option value="105">Square</option>
	// <option value="104">Triangular</option>
}
