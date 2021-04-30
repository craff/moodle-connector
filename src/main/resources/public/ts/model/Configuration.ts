import http from "axios";

export class Configuration {
   host : string;

    constructor() {
    }

    async sync () {
        let {data} = await http.get(`/moodle/conf`);
        this.host = data['host'];
    }
}