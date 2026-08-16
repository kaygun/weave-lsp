package weavelsp.model

enum Visibility:
  case Visible, Hidden

object Visibility:
  def parse(str: String): Visibility = str.toLowerCase match
    case "hidden" | "false" | "hide" => Visibility.Hidden
    case _                           => Visibility.Visible

case class BlockAttributes(
  name: Option[String] = None,
  lang: Option[String] = None,
  inputs: List[String] = Nil,
  contentType: ContentType = ContentType.PlainText,
  codeVisibility: Visibility = Visibility.Visible,
  outputVisibility: Visibility = Visibility.Visible,
  outputFile: Option[String] = None
)

sealed trait NotebookCell

case class TextCell(content: String) extends NotebookCell

case class CodeCell(
  attributes: BlockAttributes,
  code: String,
  output: Option[String] = None,
  exitCode: Int = 0
) extends NotebookCell

case class RenderCell(
  bufferName: String,
  contentType: ContentType = ContentType.PlainText,
  format: String = "pretty"
) extends NotebookCell

case class NotebookDocument(cells: List[NotebookCell])
