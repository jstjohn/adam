/**
 * Copyright (c) 2014. Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.adam.converters

import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.SparkFunSuite

class FastaConverterSuite extends SparkFunSuite {

  val converter = new FastaConverter(1000)

  sparkTest("find contig index") {
    val headerLines = sc.parallelize(Seq(
      (0, ">1 dna:chromosome chromosome:GRCh37:1:1:249250621:1"),
      (252366306, ">2 dna:chromosome chromosome:GRCh37:2:1:243199373:1"),
      (699103487, ">4 dna:chromosome chromosome:GRCh37:4:1:191154276:1"),
      (892647244, ">5 dna:chromosome chromosome:GRCh37:5:1:180915260:1"),
      (498605724, ">3 dna:chromosome chromosome:GRCh37:3:1:198022430:1")))
    val descLines = FastaConverter.getDescriptionLines(headerLines)
    val headerIndices: List[Int] = descLines.keys.toList

    assert(0 === FastaConverter.findContigIndex(252366300, headerIndices))
    assert(892647244 === FastaConverter.findContigIndex(892647249, headerIndices))
    assert(252366306 === FastaConverter.findContigIndex(498605720, headerIndices))

  }

  test("convert a single record without naming information") {
    val contig = converter.convert(None, 0, Seq("AAATTTGCGC"), None)

    assert(contig.head.getFragmentSequence.map(_.toString).reduce(_ + _) === "AAATTTGCGC")
    assert(contig.head.getContig.getContigLength === 10)
    assert(contig.head.getContig.getContigName === null)
    assert(contig.head.getDescription === null)
  }

  test("convert a single record with naming information") {
    val contig = converter.convert(Some("chr2"), 1, Seq("NNNN"), Some("hg19"))

    assert(contig.head.getFragmentSequence.map(_.toString).reduce(_ + _) === "NNNN")
    assert(contig.head.getContig.getContigLength === 4)
    assert(contig.head.getContig.getContigName === "chr2")
    assert(contig.head.getDescription === "hg19")
  }

  sparkTest("convert single fasta sequence") {
    val fasta = List((0, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGGGGGGGGGGAAAAAAAAAAGGGGGGGGGGAAAAAA"),
      (1, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (2, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (3, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (4, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (5, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (6, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (7, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (8, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (9, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (10, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (11, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (12, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (13, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (14, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (15, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    val rdd = sc.parallelize(fasta.toSeq)

    val adamFasta = FastaConverter(rdd)
    assert(adamFasta.count === 1)

    val fastaElement = adamFasta.first()
    val fastaFragmentSequence = fasta.map(_._2).reduce(_ + _)
    val convertedFragmentSequence = fastaElement.getFragmentSequence.map(_.toString).reduce(_ + _)

    assert(convertedFragmentSequence === fastaFragmentSequence)
    assert(fastaElement.getContig.getContigLength() == fastaFragmentSequence.length)
    assert(fastaElement.getContig.getContigName === null)
    assert(fastaElement.getDescription === null)
  }

  sparkTest("convert fasta with multiple sequences") {
    val fasta1 = List((0, ">chr1"),
      (1, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGGGGGGGGGGAAAAAAAAAAGGGGGGGGGGAAAAAA"),
      (2, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (3, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (4, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (5, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (6, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (7, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (8, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (9, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (10, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (11, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (12, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (13, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (14, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (15, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      (16, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    val fasta2 = List((17, ">chr2"),
      (18, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCTTTTTTTTTTCCCCCCCCCCTTTTTTTTTTCCCCCC"),
      (19, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (20, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (21, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (22, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (23, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (24, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (25, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (26, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (27, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (28, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (29, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (30, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (31, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (32, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"),
      (33, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"))
    val fasta = fasta1 ::: fasta2
    val rdd = sc.parallelize(fasta.toSeq)

    val adamFasta = FastaConverter(rdd)
    assert(adamFasta.count === 2)

    val fastaElement1 = adamFasta.filter(_.getContig.getContigName == "chr1").first()
    val fastaFragmentSequence1 = fasta1.drop(1).map(_._2).reduce(_ + _)
    val convertedFragmentSequence1 = fastaElement1.getFragmentSequence.map(_.toString).reduce(_ + _)

    assert(convertedFragmentSequence1 === fastaFragmentSequence1)
    assert(fastaElement1.getContig.getContigLength() == fastaFragmentSequence1.length)
    assert(fastaElement1.getContig.getContigName().toString === "chr1")
    assert(fastaElement1.getDescription === null)

    val fastaElement2 = adamFasta.filter(_.getContig.getContigName == "chr2").first()
    val fastaFragmentSequence2 = fasta2.drop(1).map(_._2).reduce(_ + _)
    val convertedFragmentSequence2 = fastaElement2.getFragmentSequence.map(_.toString).reduce(_ + _)

    assert(convertedFragmentSequence2 === fastaFragmentSequence2)
    assert(fastaElement2.getContig.getContigLength() == fastaFragmentSequence2.length)
    assert(fastaElement2.getContig.getContigName().toString === "chr2")
    assert(fastaElement2.getDescription === null)
  }

}
