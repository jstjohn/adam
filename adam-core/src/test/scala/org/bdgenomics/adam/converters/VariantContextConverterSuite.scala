/*
 * Copyright (c) 2013. Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.adam.converters

import scala.collection.JavaConversions._
import org.scalatest.FunSuite
import org.broadinstitute.variant.variantcontext.{ GenotypeType, Allele, VariantContextBuilder, GenotypeBuilder }
import java.lang.Integer
import org.bdgenomics.adam.models.{ ADAMVariantContext, SequenceRecord, SequenceDictionary }
import org.bdgenomics.adam.avro._
import scala.Some

class VariantContextConverterSuite extends FunSuite {
  val dictionary = SequenceDictionary(SequenceRecord("chr1", 249250621,
    "file://ucsc.hg19.fasta", "1b22b98cdeb4a9304cb5d48026a85128"))

  def gatkSNVBuilder: VariantContextBuilder = new VariantContextBuilder()
    .alleles(List(Allele.create("A", true), Allele.create("T")))
    .start(1L)
    .stop(1L)
    .chr("chr1")

  def gatkMultiAllelicSNVBuilder: VariantContextBuilder = new VariantContextBuilder()
    .alleles(List(Allele.create("A", true), Allele.create("T"), Allele.create("G")))
    .start(1L)
    .stop(1L)
    .chr("chr1")

  def adamSNVBuilder: ADAMVariant.Builder = ADAMVariant.newBuilder()
    .setContig(ADAMContig.newBuilder().setContigName("chr1").build)
    .setPosition(0L)
    .setReferenceAllele("A")
    .setVariantAllele("T")

  test("Convert GATK site-only SNV to ADAM") {
    val converter = new VariantContextConverter(Some(dictionary))

    val adamVCs = converter.convert(gatkSNVBuilder.make)
    assert(adamVCs.length === 1)
    val adamVC = adamVCs.head

    assert(adamVC.genotypes.length === 0)

    val variant = adamVC.variant

    val contig = variant.getContig
    assert(contig.getContigName === "chr1")
    assert(contig.getContigLength === 249250621)
    assert(contig.getReferenceURL === "file://ucsc.hg19.fasta")
    assert(contig.getContigMD5 === "1b22b98cdeb4a9304cb5d48026a85128")

    assert(variant.getReferenceAllele === "A")
    assert(variant.getPosition === 0L)
  }

  test("Convert GATK SNV w/ genotypes w/ phase information to ADAM") {
    val vcb = gatkSNVBuilder

    val genotypeAttributes = Map[String, Object]("PQ" -> new Integer(50), "PS" -> new Integer(1))
    val vc = vcb.genotypes(GenotypeBuilder.create("NA12878", vcb.getAlleles(), genotypeAttributes)).make()

    val converter = new VariantContextConverter(Some(dictionary))

    val adamVCs = converter.convert(vc)
    assert(adamVCs.length === 1)

    val adamGTs = adamVCs.flatMap(_.genotypes)
    assert(adamGTs.length === 1)
    val adamGT = adamGTs.head
    assert(adamGT.getAlleles.sameElements(List(ADAMGenotypeAllele.Ref, ADAMGenotypeAllele.Alt)))
    assert(adamGT.getPhaseSetId === 1)
    assert(adamGT.getPhaseQuality === 50)
  }

  test("Convert GATK SNV with different filters to ADAM") {
    val vcb = gatkSNVBuilder
    vcb.genotypes(GenotypeBuilder.create("NA12878", vcb.getAlleles))

    val converter = new VariantContextConverter(Some(dictionary))

    { // No filters
      val adamVCs = converter.convert(vcb.make)
      val adamGT = adamVCs.flatMap(_.genotypes).head
      assert(adamGT.getVariantCallingAnnotations.getVariantIsPassing === null)
    }
    { // PASSing
      vcb.unfiltered.passFilters
      val adamVCs = converter.convert(vcb.make)
      val adamGT = adamVCs.flatMap(_.genotypes).head
      assert(adamGT.getVariantCallingAnnotations.getVariantIsPassing)
    }
    { // not PASSing
      vcb.unfiltered.filter("LowMQ")
      val adamVCs = converter.convert(vcb.make)
      val adamGT = adamVCs.flatMap(_.genotypes).head
      assert(adamGT.getVariantCallingAnnotations.getVariantIsPassing === false)
      assert(adamGT.getVariantCallingAnnotations.getVariantFilters.sameElements(List("LowMQ")))
    }
  }

  test("Convert ADAM site-only SNV to GATK") {
    val vc = ADAMVariantContext(adamSNVBuilder.build)

    val converter = new VariantContextConverter(Some(dictionary))

    val gatkVC = converter.convert(vc)
    assert(gatkVC.getChr === "chr1")
    assert(gatkVC.getStart === 1)
    assert(gatkVC.getEnd === 1)
    assert(gatkVC.getReference === Allele.create("A", true))
    assert(gatkVC.getAlternateAlleles.sameElements(List(Allele.create("T"))))
    assert(!gatkVC.hasLog10PError)
    assert(!gatkVC.hasID)
    assert(!gatkVC.filtersWereApplied)
  }

  test("Convert ADAM SNV w/ genotypes to GATK") {
    val variant = adamSNVBuilder.build
    val genotype = ADAMGenotype.newBuilder
      .setVariant(variant)
      .setSampleId("NA12878")
      .setAlleles(List(ADAMGenotypeAllele.Ref, ADAMGenotypeAllele.Alt))
      .build

    val converter = new VariantContextConverter(Some(dictionary))

    val gatkVC = converter.convert(ADAMVariantContext(variant, Seq(genotype)))
    assert(gatkVC.getNSamples === 1)
    assert(gatkVC.hasGenotype("NA12878"))
    val gatkGT = gatkVC.getGenotype("NA12878")
    assert(gatkGT.getType === GenotypeType.HET)
  }

  test("Convert GATK multi-allelic sites-only SNVs to ADAM") {
    val vc = gatkMultiAllelicSNVBuilder.make
    val converter = new VariantContextConverter(Some(dictionary))

    val adamVCs = converter.convert(vc)
    assert(adamVCs.length === 2)

    for ((allele, idx) <- vc.getAlternateAlleles.zipWithIndex) {
      val adamVC = adamVCs(idx);
      assert(adamVC.variant.getReferenceAllele === vc.getReference.getBaseString)
      assert(adamVC.variant.getVariantAllele === allele.getBaseString)
    }
  }

  test("Convert GATK multi-allelic SNVs to ADAM") {
    val gb = new GenotypeBuilder("NA12878", List(Allele.create("T"), Allele.create("G")))
    gb.AD(Array(4, 2, 3)).PL(Array(59, 0, 181, 1, 66, 102))

    val vcb = gatkMultiAllelicSNVBuilder
    vcb.genotypes(gb.make())

    val converter = new VariantContextConverter(Some(dictionary))

    val adamVCs = converter.convert(vcb.make)
    assert(adamVCs.length === 2)

    for (adamVC <- adamVCs) {
      assert(adamVC.genotypes.length === 1)
      val adamGT = adamVC.genotypes.head
      assert(adamGT.getSplitFromMultiAllelic)
      assert(adamGT.getReferenceReadDepth === 4)
      assert(adamGT.getIsPhased)
    }

    val adamGT1 = adamVCs(0).genotypes.head
    val adamGT2 = adamVCs(1).genotypes.head
    assert(adamGT1.getAlleles.sameElements(List(ADAMGenotypeAllele.Alt, ADAMGenotypeAllele.OtherAlt)))
    assert(adamGT1.getAlternateReadDepth === 2)
    assert(adamGT1.getGenotypeLikelihoods.sameElements(List(59, 0, 181)))

    assert(adamGT2.getAlleles.sameElements(List(ADAMGenotypeAllele.OtherAlt, ADAMGenotypeAllele.Alt)))
    assert(adamGT2.getAlternateReadDepth === 3)
    assert(adamGT2.getGenotypeLikelihoods.sameElements(List(58, 0, 101)))
  }
}
