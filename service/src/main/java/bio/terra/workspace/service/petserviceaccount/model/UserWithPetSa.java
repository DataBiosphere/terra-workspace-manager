package bio.terra.workspace.service.petserviceaccount.model;

/**
 * Internal representation of a user with their pet service account.
 *
 * <p>Both the user and pet are represented by email addresses.
 */
public class UserWithPetSa {
  private final String userEmail;
  private final String petEmail;

  public UserWithPetSa(String userEmail, String petEmail) {
    this.userEmail = userEmail;
    this.petEmail = petEmail;
  }

  public String getUserEmail() {
    return userEmail;
  }

  public String getPetEmail() {
    return petEmail;
  }
}
