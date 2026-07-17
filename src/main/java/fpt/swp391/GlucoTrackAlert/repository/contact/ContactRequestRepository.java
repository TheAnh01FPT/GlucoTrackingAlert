package fpt.swp391.GlucoTrackAlert.repository.contact;

import fpt.swp391.GlucoTrackAlert.model.contact.ContactRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {
    
    Page<ContactRequest> findByStatus(String status, Pageable pageable);
}
