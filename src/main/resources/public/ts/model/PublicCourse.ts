import {Shareable, notify} from "entcore";
import http from 'axios';
import {Mix,Selectable,Selection} from 'entcore-toolkit';
import {Author, Course} from "./Course";

export class PublicCourse extends Course implements Shareable{

    coursesId : Array<number>;
    disciplines : Array<string>;
    levels : Array<string>;
    plain_text: Array<string> = [];
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

export class Level implements Selectable {
    id : number;
    label : string;

    selected : boolean = false;

    constructor(id?: number, label?: string) {
        this.id = id;
        this.label = label;
    }
    toString = () => this.label;

}

export class Levels extends Selection<Level> {

    constructor () {
        super([]);
    }
    async sync (): Promise<void> {
        let { data } = await http.get(`/moodle/levels`);
        this.all = Mix.castArrayAs(Level, data);
    }
}


export class Discipline implements Selectable {
    id : number;
    label : string;
    selected : boolean = false;

    constructor(id?: number, label?: string) {
        this.id = id;
        this.label = label;
    }
    toString = () => this.label;
}

export class Disciplines extends Selection<Discipline> {

    constructor () {
        super([]);
    }
    async sync (): Promise<void> {
        let { data } = await http.get(`/moodle/disciplines`);
        this.all = Mix.castArrayAs(Discipline, data);
    }
}
