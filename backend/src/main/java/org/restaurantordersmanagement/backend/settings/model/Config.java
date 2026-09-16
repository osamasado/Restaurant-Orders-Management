package org.restaurantordersmanagement.backend.settings.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.restaurantordersmanagement.backend.i18n.Language;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Config {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String currencyCode;

    private String currencySymbol;

    @Enumerated(EnumType.STRING)
    private SymbolPosition symbolPosition;

    private BigDecimal taxRate;

    @Enumerated(EnumType.STRING)
    private Language defaultLanguage;

    @ElementCollection
    @CollectionTable(
            name = "config_payment_method",
            joinColumns = @JoinColumn(name = "config_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private Set<PaymentMethod> enabledPaymentMethods = new HashSet<>();

}
