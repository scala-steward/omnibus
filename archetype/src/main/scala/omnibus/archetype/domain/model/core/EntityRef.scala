package omnibus.archetype.domain.model.core

import scala.reflect.ClassTag
import scala.language.existentials
import omnibus.commons.util._


sealed trait EntityRef extends ((Symbol) => Any) with Ordered[EntityRef] {
  type Source <: Entity
  type ID = Source#ID
  type TID = Source#TID

  def get( property: Symbol ): Option[Any]

  def id: TID
  def name: String

  def identify: String = name + ":" + id
  override def toString: String = identify
}

object EntityRef {
  type Aux[E <: Entity] = EntityRef { type Source = E }

  val ID: Symbol = 'id
  val NAME: Symbol = 'name


  // def apply[T <: Entity : ClassTag]( entity: T ): EntityRef = {
  //   EntityRefImpl[T]( id = entity.id, name = entity.name, clazz = implicitly[ClassTag[T]].runtimeClass )
  // }

  // def apply( e: Entity, meta: (Symbol, Any)* ): EntityRef = {
  //   EntityRefImpl( id = e.id, name = e.name, clazz = e.getClass, meta = Map( meta:_* ) )
  // }

  // def apply[T <: Entity : ClassTag]( id: T#ID, name: String ): EntityRef = {
  //   EntityRefImpl[T]( id = id, name = name, clazz = implicitly[ClassTag[T]].runtimeClass )
  // }

  // def apply( id: Entity#ID, name: String, clazz: Class[_], meta: (Symbol, Any)* ): EntityRef = {
  //   EntityRefImpl( id = id, name = name, clazz = clazz, meta = Map( meta:_* ) )
  // }

  def unapply( ref: EntityRef ): Option[(ref.ID, String)] = Some( (ref.id, ref.name) )

  trait EntityRefLike extends EntityRef with Equals {
    def meta: Map[Symbol, Any]

    override def apply( property: Symbol ): Any = {
      get( property ) getOrElse {
        throw new NoSuchElementException( s"${toString} does not contain requested property: $property" )
      }
    }

    override def get( property: Symbol ): Option[Any] = property match {
      case ID => Some( id )
      case NAME => Some( name )
      case p => meta get p
    }

    override def compare( rhs: EntityRef ): Int = this.## compare rhs.##

    override def hashCode: Int = 41 * ( 41 + id.## )

    override def equals( rhs: Any ): Boolean = rhs match {
      case that: EntityRefLike => {
        if ( this eq that ) true
        else {
          ( that.## == this.## ) &&
          ( that canEqual this ) &&
          ( this.id == that.id )
        }
      }

      case _ => false
    }

    override def toString: String = {
      var result = s"EntityRef(name=$name, id=$id, class=${getClass.safeSimpleName}"
      if ( !meta.isEmpty ) result += s", meta=${meta}"
      result += ")"
      result
    }
  }
}