import {Shareable, notify} from "entcore";
import http from 'axios';
import {Author, Course} from "./Course";
import {Labels} from "./Label";

export class PublicCourse extends Course implements Shareable{

    coursesId : Array<number>;
    disciplines : Labels;
    levels : Labels;
    plain_text: Labels;
    fullname : string;
    summary : string;
    imageurl : string;
    auteur: Author;

    constructor() {
        super();
        this.coursesId = [];
    }

    toJson () {
        this.coursesId.push(this.courseid);
        return {
            coursesId: this.coursesId,
            disciplines: this.disciplines,
            levels: this.levels,
            plain_text: this.plain_text,
            role: this.role,
            title: this.fullname,
            description: this.summary,
            urlImage: this.imageurl,
            authors: this.auteur
        };
    }

    async publish():Promise<void> {
        try {
            let {data} = await http.post(`/moodle/course/publish`, this.toJson());
            this.courseid = data.id;
            this.coursesId = [];
        } catch (e) {
            notify.error('moodle.course.publish.err');
        }
    }

    async modify():Promise<void> {
        try {
            let {data} = await http.post(`/moodle/metadata/update`, this.toJson());
            this.courseid = data.id;
            this.coursesId = [];
        } catch (e) {
            notify.error("moodle.course.publish.update.err");
        }
    }
}
