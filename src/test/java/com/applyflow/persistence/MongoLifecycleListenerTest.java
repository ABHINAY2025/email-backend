package com.applyflow.persistence;

import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.Note;
import com.applyflow.entity.User;
import com.applyflow.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoLifecycleListenerTest {

    private final SequenceGenerator sequences = mock(SequenceGenerator.class);
    private final MongoLifecycleListener listener = new MongoLifecycleListener(sequences);

    {
        when(sequences.next(anyString())).thenReturn(100L);
    }

    private void save(Object doc) {
        listener.onBeforeConvert(new BeforeConvertEvent<>(doc, "c"));
    }

    @Test
    void refusesOwnedDocumentsWithoutAnOwner() {
        assertThatThrownBy(() -> save(new JobApplication())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without an owner");
    }

    @Test
    void stampsTheCurrentOwner() {
        JobApplication a = new JobApplication();
        CurrentUser.runAs(7L, () -> save(a));
        assertThat(a.getUserId()).isEqualTo(7L);
        assertThat(a.getId()).isEqualTo(100L);
    }

    @Test
    void keepsAnExplicitOwner() {
        JobApplication a = new JobApplication();
        a.setUserId(3L);
        CurrentUser.runAs(7L, () -> save(a));
        assertThat(a.getUserId()).isEqualTo(3L);
    }

    @Test
    void usersAreNotOwnedDocuments() {
        User u = new User();
        save(u);
        assertThat(u.getId()).isEqualTo(100L);
    }

    @Test
    void childrenAdoptTheirParentsOwner() {
        EmailAccount account = new EmailAccount();
        account.setUserId(5L);
        JobApplication app = new JobApplication();
        app.setEmailAccount(account);
        assertThat(app.getUserId()).isEqualTo(5L);

        Note n = new Note();
        n.setApplication(app);
        assertThat(n.getUserId()).isEqualTo(5L);
    }
}
