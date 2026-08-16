package weavelsp.piping

import weavelsp.model.{CodeCell, NotebookDocument}

import scala.collection.mutable

object DependencyGraph:

  case class ExecutionPlan(
    orderedCodeCells: List[CodeCell],
    missingDependencies: Map[String, List[String]] // cellName -> list of missing input buffers
  )

  /**
   * Sort code cells in topological order according to their input buffer dependencies.
   * Only cells with an explicit name attribute (name:...) are treated as pipeline execution cells.
   */
  def buildExecutionPlan(doc: NotebookDocument): ExecutionPlan =
    val codeCells = doc.cells.collect { case c: CodeCell if c.attributes.name.isDefined => c }

    // Map buffer names to producing cells
    val producerMap = mutable.Map[String, CodeCell]()
    codeCells.foreach { cell =>
      cell.attributes.name.foreach { name =>
        producerMap(name) = cell
      }
    }

    val missing = mutable.Map[String, List[String]]()
    val visited = mutable.Set[CodeCell]()
    val visiting = mutable.Set[CodeCell]()
    val order = mutable.ArrayBuffer[CodeCell]()

    def visit(cell: CodeCell): Unit =
      if !visited.contains(cell) then
        visiting.add(cell)
        val cellId = cell.attributes.name.getOrElse("unnamed")

        cell.attributes.inputs.foreach { inputBufName =>
          producerMap.get(inputBufName) match
            case Some(producerCell) =>
              if !visiting.contains(producerCell) then
                visit(producerCell)
            case None =>
              // Input buffer comes from outside or hasn't been produced by a cell
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
