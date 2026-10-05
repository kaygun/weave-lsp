package weavelsp.piping

import weavelsp.model.{BlockAttributes, CodeCell, NotebookDocument}
import scala.collection.mutable

object DependencyGraph:

  case class ExecutionPlan(
    orderedCodeCells: List[CodeCell],
    missingDependencies: Map[String, List[String]] // cellName -> list of missing input buffers
  )

  /**
   * Sort code cells in topological order according to their input buffer dependencies and language session order.
   * Named cells or cells with eval:true are treated as pipeline execution cells unless eval:false.
   */
  def buildExecutionPlan(doc: NotebookDocument): ExecutionPlan =
    var anonCount = 0
    val codeCells = doc.cells.zipWithIndex.collect {
      case (c: CodeCell, idx) if shouldExecute(c) =>
        val cellName = c.attributes.name.getOrElse {
          anonCount += 1
          s"_cell_$anonCount"
        }
        val updatedAttrs = c.attributes.copy(name = Some(cellName))
        c.copy(attributes = updatedAttrs)
    }

    // Map buffer names to producing cells
    val producerMap = mutable.Map[String, CodeCell]()
    codeCells.foreach { cell =>
      cell.attributes.name.foreach { name =>
        require(name.nonEmpty, "Cell names must not be empty")
        require(!producerMap.contains(name), s"Duplicate cell name: '$name'")
        producerMap(name) = cell
      }
    }

    // Map each cell to the preceding cell of the same language to preserve state order
    val lastCellByLang = mutable.Map[String, CodeCell]()
    val langDependencies = mutable.Map[CodeCell, Option[CodeCell]]()
    codeCells.foreach { cell =>
      val langKey = cell.attributes.lang.getOrElse("bash").toLowerCase
      langDependencies(cell) = lastCellByLang.get(langKey)
      lastCellByLang(langKey) = cell
    }

    val missing = mutable.Map[String, List[String]]()
    val visited = mutable.Set[CodeCell]()
    val visiting = mutable.Set[CodeCell]()
    val order = mutable.ArrayBuffer[CodeCell]()

    def visit(cell: CodeCell): Unit =
      if !visited.contains(cell) then
        visiting.add(cell)
        val cellId = cell.attributes.name.getOrElse("unnamed")

        // 1. Visit preceding cell of the same language to maintain stateful execution order
        langDependencies.get(cell).flatten.foreach { priorLangCell =>
          if !visiting.contains(priorLangCell) then
            visit(priorLangCell)
        }

        // 2. Visit input buffer producer cells
        cell.attributes.inputs.foreach { inputBufName =>
          producerMap.get(inputBufName) match
            case Some(producerCell) =>
              require(!visiting.contains(producerCell),
                s"Dependency cycle: '$cellId' depends on '$inputBufName'")
              visit(producerCell)
            case None =>
              val currentMissing = missing.getOrElse(cellId, Nil)
              missing(cellId) = currentMissing :+ inputBufName
        }

        visiting.remove(cell)
        visited.add(cell)
        order += cell

    codeCells.foreach(visit)

    ExecutionPlan(
      orderedCodeCells = order.toList,
      missingDependencies = missing.toMap
    )

  private def shouldExecute(cell: CodeCell): Boolean =
    cell.attributes.eval match
      case Some(false) => false
      case Some(true)  => true
      case None        => cell.attributes.name.isDefined
