package com.panggilan.loket.dto;

import javax.validation.constraints.NotBlank;

public class CreateCounterRequest {

        @NotBlank(message = "Id loket wajib diisi")
        private String id;

        @NotBlank(message = "Nama loket wajib diisi")
        private String name;

        public CreateCounterRequest() {
                // Default constructor required for JSON binding
        }

        public CreateCounterRequest(String id, String name) {
                this.id = id;
                this.name = name;
        }

        public String getId() {
                return id;
        }

        public void setId(String id) {
                this.id = id;
        }

        public String getName() {
                return name;
        }

        public void setName(String name) {
                this.name = name;
        }
}
