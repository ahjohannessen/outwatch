package outwatch

import cats.effect.IO
import outwatch.Sink.{ObservableSink, SubjectSink}
import rxscalajs.Observable


trait PipeOps[-I, +O] { self : Pipe[I, O]  =>

  def mapSink[I2](f: I2 => I): Pipe[I2, O] = Pipe(redirectMap(f), self)

  def collectSink[I2](f: PartialFunction[I2, I]): Pipe[I2, O] = Pipe(self.redirect(_.collect(f)), self)

  def mapSource[O2](f: O => O2): Pipe[I, O2] = Pipe(self, self.map(f))

  def collectSource[O2](f: PartialFunction[O, O2]): Pipe[I, O2] = Pipe(self, self.collect(f))

  def filterSource(f: O => Boolean): Pipe[I, O] = Pipe(self, self.filter(f))

  def mapHandler[I2, O2](f: I2 => I)(g: O => O2): Pipe[I2, O2] = Pipe(self.redirectMap(f), self.map(g))

  def collectHandler[I2, O2](f: PartialFunction[I2, I])(g: PartialFunction[O, O2]): Pipe[I2, O2] = Pipe(
    self.redirect(_.collect(f)), self.collect(g)
  )

  def transformSink[I2](f: Observable[I2] => Observable[I]): Pipe[I2, O] = Pipe(self.redirect(f), self)

  def transformSource[O2](f: Observable[O] => Observable[O2]): Pipe[I, O2] = Pipe(self, f(self))

  def transformHandler[I2, O2](f: Observable[I2] => Observable[I])(g: Observable[O] => Observable[O2]): Pipe[I2, O2] =
    Pipe(self.redirect(f), g(self))
}


object Pipe {
  private[outwatch] def apply[I, O](sink: Sink[I], source: Observable[O]): Pipe[I, O] =
    new ObservableSink[I, O](sink, source) with PipeOps[I, O]

  implicit class FilterSink[I, +O](handler: Pipe[I, O]) {
    def filterSink(f: I => Boolean): Pipe[I, O] = Pipe(handler.redirect(_.filter(f)), handler)
  }

  /**
    * This function also allows you to create initial values for your newly created Handler.
    * This is equivalent to calling `startWithMany` with the given values.
    * @param seeds a sequence of initial values that the Handler will emit.
    * @tparam T the type parameter of the elements
    * @return the newly created Handler.
    */
  def create[T](seeds: T*): IO[Pipe[T, T]] = create[T].map { handler =>
    if (seeds.nonEmpty) {
      handler.transformSource(_.startWithMany(seeds: _*))
    }
    else {
      handler
    }
  }

  def create[T]: IO[Pipe[T, T]] = IO {
    val sink = SubjectSink[T]()
    Pipe(sink, sink)
  }
}
