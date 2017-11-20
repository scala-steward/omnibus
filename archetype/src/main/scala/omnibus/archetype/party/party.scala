package omnibus.archetype.party

import org.joda.time.LocalDate
import omnibus.archetype.domain.model.core.Entity
import omnibus.archetype.{Address, OrganizationName, PersonName}
import relationship.{PartyRole, PartyRoleLike}
import omnibus.commons.util._


//todo: rethink all of archetype in terms of Aux pattern and functional free monads or Reader monad
trait Party extends Entity with Equals {
  def addresses: Seq[Address]
  // def roles: Seq[PartyRoleLike[ID]]
  def roles: Seq[PartyRole]
  // def preferences: Seq[Preference]

  override def canEqual( rhs: Any ): Boolean = rhs.isInstanceOf[Party]

  override def equals( rhs: Any ): Boolean = rhs match {
    case that: Party => {
      if ( this eq that ) true
      else {
        ( that.## == this.## ) &&
        ( that canEqual this ) &&
        ( this.id == that.id )
      }
    }

    case _ => false
  }

  override def hashCode: Int = 41 * ( 41 + id.## )

  override def toString: String = s"""${getClass.safeSimpleName}(id-${id}:${name})"""
}


trait Person extends Party { 
  def personName: PersonName

  def otherPersonNames: Seq[PersonName] = Seq()

  override def name: String = personName.toString

  def dateOfBirth: Option[LocalDate] = None

  override def canEqual( rhs: Any ): Boolean = rhs.isInstanceOf[Person]
}


trait Organization extends Party {
  def organizationName: OrganizationName

  def otherOrganizationNames: Seq[OrganizationName] = Seq()

  override def name: String = organizationName.toString

  override def canEqual( rhs: Any ): Boolean = rhs.isInstanceOf[Organization]
}


trait Company extends Organization