package com.shopwise.customer.internal;

record MeView(long id, String email, String name, boolean hasPassword, boolean googleLinked, boolean admin) {

    static MeView of(Customer c) {
        return new MeView(c.getId(), c.getEmail(), c.getName(), c.getPasswordHash() != null,
                c.getGoogleSubject() != null, c.getRole() == Customer.Role.ADMIN);
    }
}
