package bio.terra.workspace.service.petserviceaccount;

import com.google.api.services.iam.v1.model.Binding;
import com.google.api.services.iam.v1.model.Policy;
import java.util.ArrayList;

// Static class of methods shared by PetSaService and GrantService
public class PetSaUtils {
  public static final String SERVICE_ACCOUNT_USER_ROLE = "roles/iam.serviceAccountUser";

  public static Binding getOrCreateBinding(Policy saPolicy) {
    // Populate a binding list, if there is none in the policy
    if (saPolicy.getBindings() == null) {
      saPolicy.setBindings(new ArrayList<>());
    }

    // Find the binding, if any
    for (Binding binding : saPolicy.getBindings()) {
      if (binding.getRole().equals(SERVICE_ACCOUNT_USER_ROLE)) {
        return binding;
      }
    }

    // Create if it doesn't exist
    Binding newBinding = new Binding().setRole(SERVICE_ACCOUNT_USER_ROLE);
    saPolicy.getBindings().add(newBinding);
    return newBinding;
  }

  /**
   * @param saPolicy policy to modify
   * @param member member to add
   * @return true if member was added; false if member was already there
   */
  public static boolean addSaMember(Policy saPolicy, String member) {
    Binding binding = getOrCreateBinding(saPolicy);
    if (binding.getMembers() == null) {
      binding.setMembers(new ArrayList<>());
    }

    if (binding.getMembers().contains(member)) {
      return false;
    }
    binding.getMembers().add(member);
    return true;
  }

  /**
   * @param saPolicy policy to mutate
   * @param member member to remove
   * @return true if we removed the member; false if the member was not there
   */
  public static boolean removeSaMember(Policy saPolicy, String member) {
    if (saPolicy.getBindings() == null) {
      return false;
    }
    Binding binding = getOrCreateBinding(saPolicy);
    if (binding.getMembers() == null) {
      return false;
    }
    return binding.getMembers().remove(member);
  }
}
