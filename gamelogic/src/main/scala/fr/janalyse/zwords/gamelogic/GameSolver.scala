package fr.janalyse.zwords.gamelogic

object GameSolver {
  def knownPlaces(row: GuessRow): Map[Int, Char] =
    row.state.zipWithIndex.collect { case (GoodPlaceCell(ch), index) => index -> ch }.toMap

  def knownPlacesIndices(row: GuessRow): Set[Int] =
    row.state.zipWithIndex.collect { case (GoodPlaceCell(ch), index) => index }.toSet

  def possiblePlaces(row: GuessRow): Map[Char, Set[Int]] =
    val state = row.state
    state.zipWithIndex.collect { case (WrongPlaceCell(ch), index) =>
      ch -> state.zipWithIndex.flatMap {
        case (WrongPlaceCell(ch), currentIndex) if currentIndex != index => Some(currentIndex)
        case (NotUsedCell(ch), currentIndex)                             => Some(currentIndex)
        case (_, _)                                                      => None
      }.toSet
    }.toMap

  def impossiblePlaces(row: GuessRow): Map[Int, Set[Char]] =
    val state = row.state
    state
      .collect { case NotUsedCell(ch) => ch }
      .toSet
      .flatMap(ch =>
        state.zipWithIndex.flatMap {
          case (WrongPlaceCell(_), currentIndex) => Some(currentIndex -> ch)
          case (NotUsedCell(_), currentIndex)    => Some(currentIndex -> ch)
          case (_, _)                            => None
        }
      )
      .groupMap((pos, ch) => pos)((pos, ch) => ch)

  // ===============================================================================================

  def knownPlaces(board: Board): Map[Int, Char] = board.rows.flatMap(knownPlaces).toMap

  def knownPlacesIndices(board: Board): Set[Int] = board.rows.flatMap(knownPlacesIndices).toSet

  def possiblePlaces(board: Board): Map[Char, Set[Int]] =
    board.rows
      .flatMap(possiblePlaces)
      .groupMapReduce((ch, _) => ch)((_, pos) => pos)((a, b) => a.intersect(b))
      .map((ch, pos) => ch -> pos.removedAll(knownPlacesIndices(board)))
      .filterNot((ch, pos) => pos.isEmpty)

  def impossiblePlaces(board: Board): Map[Int, Set[Char]] =
    board.rows
      .map(impossiblePlaces)
      .reduceOption((a, b) =>
        (a.toList ++ b.toList)
          .groupMapReduce((position, _) => position)((_, chars) => chars)((aChars, bChars) => aChars ++ bChars)
      )
      .getOrElse(Map.empty)
      .removedAll(knownPlacesIndices(board))
      .filterNot((pos, chars) => chars.isEmpty)

}
