import http from "axios";

export class Configuration {
    id_bp_category : number;

    constructor() {
    }

    async sync () {
        let {data} = await http.get(`/moodle/conf/public`);
        this.id_bp_category = data['publicBankCategoryId'];
    }
}