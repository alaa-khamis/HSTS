package org.group7.server;

import org.group7.entities.*;

import org.group7.server.ocsf.AbstractServer;
import org.group7.server.ocsf.ConnectionToClient;
import org.group7.server.ocsf.SubscribedClient;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.Session;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.io.IOException;
import java.util.*;
import java.util.HashMap;



public class Server extends AbstractServer {

    public static Session session;

    private static ArrayList<SubscribedClient> SubscribersList = new ArrayList<>();


    public Server(int port) {
        super(port);
        SessionFactory sessionFactory = getSessionFactory();
        session = sessionFactory.openSession();
        generate();     // Generate data
    }


    private static SessionFactory getSessionFactory() throws HibernateException {
        Configuration configuration = new Configuration();

        configuration.addAnnotatedClass(Course.class);
        configuration.addAnnotatedClass(Exam.class);
        configuration.addAnnotatedClass(ExecutableExam.class);
        configuration.addAnnotatedClass(ExtraTime.class);
        configuration.addAnnotatedClass(Principal.class);
        configuration.addAnnotatedClass(Question.class);
        configuration.addAnnotatedClass(Result.class);
        configuration.addAnnotatedClass(Student.class);
        configuration.addAnnotatedClass(Subject.class);
        configuration.addAnnotatedClass(Teacher.class);
        configuration.addAnnotatedClass(User.class);
        configuration.addAnnotatedClass(ManualResult.class);

        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().applySettings(
                configuration.getProperties()).build();

        return configuration.buildSessionFactory(serviceRegistry);
    }

    public static <T> List<T> getAll(Class<T> _class) {
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(_class);
        query.from(_class);
        List<T> data = session.createQuery(query).getResultList();

        return data;
    }

    public void sendToAllClients(Message message, ConnectionToClient client) {
        try {
            for (SubscribedClient SubscribedClient : SubscribersList) {

                if (SubscribedClient.getClient() == client && message.getMessage().equals("#TimeRequestApproved"))
                    continue;

                SubscribedClient.getClient().sendToClient(message);
                System.out.println(SubscribedClient.getClient().getId());
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    @Override
    protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
        Message message = (Message) msg;
        String req = message.getMessage();

        try {
            switch (req) {
                case "#NewClient" -> {
                    SubscribedClient connection = new SubscribedClient(client);
                    SubscribersList.add(connection);
                }
                case "#GetStudents" -> {
                    List<Student> students = getAll(Student.class);
                    client.sendToClient(new Message(students, "#GotStudents"));
                }
                case "#GetExams" -> {
                    List<Exam> exams = getAll(Exam.class);
                    client.sendToClient(new Message(exams, "#gotExams"));
                }
                case "#GetSubjects" -> {
                    List<Subject> subjects = getAll(Subject.class);
                    client.sendToClient(new Message(subjects, "#GotSubjects"));
                }
                case "#GetCourses" -> {
                    List<Course> courses = getAll(Course.class);
                    client.sendToClient(new Message(courses, "#getCourses"));
                }
                case "#GetTeachers" -> {
                    List<Teacher> teachers = getAll(Teacher.class);
                    client.sendToClient(new Message(teachers, "#getTeachers"));
                }
                case "#GetGrades" -> {
                    client.sendToClient(new Message(message.getObject(), "#GotGrades"));
                }
                case "#UpdateGrade" -> {

                    Object[] obj = (Object[]) message.getObject();

                    try {
                        session.beginTransaction();

                        Result grade = (Result) obj[0];
                        int newValue = Integer.parseInt((String) obj[1]);

                        grade.setGrade(newValue);

                        session.merge(grade);

                        session.flush();

                        session.getTransaction().commit();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    client.sendToClient(new Message(obj[2], "#GradeUpdated"));
                }

                case "#Logout" -> {

                    String id = ((User) message.getObject()).getUsername();

                    try {
                        session.beginTransaction();

                        User user = session.find(User.class, id);

                        user.setConnected(false);
                        session.save(user);
                        session.flush();

                        client.sendToClient(new Message(user, "#Logout"));

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                case "#Login" -> {

                    Object[] obj = (Object[]) message.getObject();

                    try {
                        session.beginTransaction();

                        User user = session.find(User.class, obj[0]);

                        String pass = obj[1].toString();

                        if (user == null || !Objects.equals(user.getPassword(), pass)) {
                            client.sendToClient(new Message(null, "#Login_Fail"));

                        } else {

                            if (user.isConnected()) {
                                client.sendToClient(new Message(user, "#Login_Connected"));
                            } else {
                                user.setConnected(true);
                                session.save(user);
                                session.flush();

                                client.sendToClient(new Message(user, "#Login_Success"));
                            }
                        }

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                case "#SendTimeRequest" -> {

                    Object[] obj = (Object[]) message.getObject();

                    try {
                        session.beginTransaction();

                        ExecutableExam exam = session.find(ExecutableExam.class, obj[0]);

                        if (exam == null) {
                            client.sendToClient(new Message(obj[0], "#ExtraTime_Fail"));
                        } else {

                            int time = (int) obj[2];

                            ExtraTime et = new ExtraTime(exam, (String) obj[1], time);

                            session.save(et);
                            session.flush();

                            List<Principal> principals = getAll(Principal.class);

                            for (Principal p : principals) {
                                p.getRequestList().add(et);
                                session.save(p);
                                session.flush();
                            }

                            client.sendToClient(new Message(null, "#ExtraTime_Success"));
                        }
                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#GetTimeRequests" -> {

                    try {
                        session.beginTransaction();

                        Principal principal = (Principal) message.getObject();

                        principal = session.find(Principal.class, principal.getUsername());

                        List<ExtraTime> requests = principal.getRequestList();

                        client.sendToClient(new Message(requests, "#GotRequestsList"));

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#ApproveTimeRequest" -> {

                    try {
                        session.beginTransaction();

                        ExtraTime request = (ExtraTime) message.getObject();
                        request = session.find(ExtraTime.class, request.getRequestId());
                        request.setStatus(true);
                        session.save(request);
                        session.flush();

                        ExecutableExam exam = session.find(ExecutableExam.class, request.getExam().getExamId());
                        exam.setTime(exam.getTime() + request.getExtra());
                        session.save(exam);
                        session.flush();

                        session.getTransaction().commit();

                        sendToAllClients(new Message(request, "#TimeRequestApproved"), client);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#DenyTimeRequest" -> {

                    try {
                        session.beginTransaction();

                        ExtraTime request = (ExtraTime) message.getObject();
                        request = session.find(ExtraTime.class, request.getRequestId());
                        session.delete(request);
                        session.flush();

                        List<Principal> principals = getAll(Principal.class);

                        for (Principal p : principals) {
                            ExtraTime finalRequest = request;
                            p.getRequestList().removeIf(item -> item.getRequestId() == finalRequest.getRequestId());
                            session.save(p);
                            session.flush();
                        }

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#GetTeacherExams" -> {

                    try {
                        session.beginTransaction();

                        Teacher teacher = (Teacher) message.getObject();

                        teacher = session.find(Teacher.class, teacher.getUsername());

                        List<Course> courses = teacher.getCourseList();

                        List<Exam> exams = new ArrayList<>();

                        for (Course course : courses) {
                            List<Exam> temp = course.getExamList();
                            if (temp != null) {
                                exams.addAll(temp);
                            }
                        }

                        List<ExecutableExam> executableExams = getAll(ExecutableExam.class);

                        Object[] obj = {exams, executableExams};

                        client.sendToClient(new Message(obj, "#GotTeacherExams"));

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#SaveExecutable" -> {

                    Object[] obj = (Object[]) message.getObject();

                    try {
                        session.beginTransaction();

                        Teacher teacher = session.find(Teacher.class, ((Teacher) obj[2]).getUsername());

                        ExecutableExam exam = new ExecutableExam((String) obj[1], (Exam) obj[0], teacher);
                        teacher.getExamList().add(exam);

                        session.save(exam);
                        session.save(teacher);
                        session.flush();

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#StartExam" -> {

                    String examId = (String) message.getObject();

                    try {
                        session.beginTransaction();

                        ExecutableExam exam = session.find(ExecutableExam.class, examId);

                        if (exam == null) {
                            client.sendToClient(new Message(examId, "#StartExam_Incorrect"));
                        } else {
                            if (exam.getExam().getType() == 1) {
                                client.sendToClient(new Message(exam, "#StartExam_Auto"));
                            } else {
                                client.sendToClient(new Message(exam, "#StartExam_Manual"));
                            }
                        }

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#saveExam" -> {

                    try {
                        session.beginTransaction();

                        Exam exam = (Exam) message.getObject();

                        List<Question> questions = exam.getQuestionList();
                        List<Question> temp = new ArrayList<>();
                        for (Question t : questions) {
                            Question question = session.find(Question.class, t.getQuestionId());
                            temp.add(question);
                        }
                        exam.setQuestionList(temp);

                        Teacher teacher = session.find(Teacher.class, exam.getCreator().getUsername());
                        exam.setCreator(teacher);
                        teacher.getCreatedExams().add(exam);

                        Course course = session.find(Course.class, exam.getCourse().getCourseId());
                        exam.setCourse(course);
                        course.getExamList().add(exam);

                        session.save(exam);
                        session.save(teacher);
                        session.save(course);

                        for (Question t : temp) {
                            Question question = session.find(Question.class, t.getQuestionId());
                            question.getExamList().add(exam);
                            session.save(course);
                        }

                        session.flush();
                        session.getTransaction().commit();

                        client.sendToClient(new Message(null, "#ExamSaved"));


                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                case "#SaveQuestion" -> {

                    try {

                        session.beginTransaction();

                        Question question = (Question) message.getObject();

                        List<Course> courses = question.getCourseList();
                        List<Course> temp = new ArrayList<>();
                        for (Course t : courses) {
                            Course course = session.find(Course.class, t.getCourseId());
                            temp.add(course);
                        }

                        question.setCourseList(temp);

                        Subject subject = session.find(Subject.class, question.getSubject().getSubjectId());
                        question.setSubject(subject);
                        subject.getQuestionList().add(question);

                        session.save(subject);
                        session.save(question);

                        for (Course t : temp) {
                            Course course = session.find(Course.class, t.getCourseId());
                            course.getQuestionList().add(question);
                            session.save(course);
                        }

                        session.flush();
                        session.getTransaction().commit();
                        client.sendToClient(new Message(question, "#PrepareQuestion_Success"));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                case "#FinishExam" -> {

                    Object[] objects = (Object[]) message.getObject();

                    try {
                        session.beginTransaction();

                        User user = session.find(User.class, ((User) objects[1]).getUsername());
                        Result result = (Result) objects[0];

                        ExecutableExam exam = session.find(ExecutableExam.class, ((ExecutableExam) objects[2]).getExamId());
                        double[] arr = (double[]) objects[3];
                        exam.setAverage(arr[0]);
                        exam.setMedian(arr[1]);
                        exam.setDistribution((int[]) objects[4]);
                        session.save(exam);

                        result.setExam(exam);
                        session.save(result);

                        Student student = (Student) user;
                        student.getExamList().add(exam);
                        student.getResultList().add(result);
                        session.save(student);

                        exam.getStudentList().add(student);
                        session.save(exam);
                        session.flush();

                        session.getTransaction().commit();

                        client.sendToClient(new Message(null, "#ExamFinished"));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#FinishManExam" -> {
                    try {
                        session.beginTransaction();

                        ManualResult result = (ManualResult) message.getObject();

                        Student student = result.getStudent();
                        student = session.find(Student.class, student.getUsername());
                        student.getManualResultList().add(result);
                        session.save(student);

                        ExecutableExam exam = result.getExam();
                        exam = session.find(ExecutableExam.class, exam.getExamId());
                        exam.getStudentList().add(student);
                        session.save(exam);

                        session.save(result);
                        session.flush();

                        session.getTransaction().commit();

                        client.sendToClient(new Message(null, "#ExamFinished"));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#GetTeacherCourses" -> {

                    try {
                        session.beginTransaction();

                        Teacher teacher = (Teacher) message.getObject();

                        teacher = session.find(Teacher.class, teacher.getUsername());

                        List<Course> courses = teacher.getCourseList();

                        client.sendToClient(new Message(courses, "#GotTeacherCourses"));

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } case "#getExecutableExam" -> {
                    List<ExecutableExam> executable = getAll(ExecutableExam.class);
                    client.sendToClient(new Message(executable, "#GotExecutableExam"));
                }
                case "#GetStudentResults" -> {

                    try {
                        session.beginTransaction();

                        Student student = (Student) message.getObject();

                        student = session.find(Student.class, student.getUsername());

                        List<Result> results = student.getResultList();

                        client.sendToClient(new Message(results, "#GotStudentResults"));

                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case "#approveResult" -> {

                    Object[] objects = (Object[]) message.getObject();

                    try {
                        session.beginTransaction();

                        Result result = session.find(Result.class, ((Result) objects[0]).getResultId());
                        ExecutableExam exam = session.find(ExecutableExam.class, result.getExam().getExamId());
                        result.setStatus(true);
                        result.setGrade((Integer) objects[2]);
                        result.setTeacherNote((String) objects[3]);
                        exam.setAverage((double) objects[4]);
                        exam.setMedian((double) objects[5]);
                        exam.setDistribution((int[]) objects[6]);

                        session.save(result);
                        session.save(exam);

                        session.flush();
                        session.getTransaction().commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void serverClosed() {
        super.serverClosed();
        stopListening();
        session.close();
    }

    public static void generate() {

        session.beginTransaction();

        // Add principal
        Principal principal = new Principal("admin", "admin", "Dr. Ahed", "Khatib");
        session.save(principal);
        session.flush();

        // Add students
        Student alaa = new Student("alaakhamis", "123", "Alaa", "Khamis", "123456789");
        Student ahed = new Student("ahedkhatib", "321", "Ahed", "Khatib", "123123123");
        Student lana = new Student("lanaasadi", "123", "Lana", "Asadi", "123456789");
        Student ebraheem = new Student("ebraheem", "321", "Ebraheem", "Ebraheem", "987564321");
        Student zinab = new Student("zinabdahle", "123", "Zinab", "Dahle", "123456321");
        Student adan = new Student("adanhammoud", "321", "Adan", "Hammoud", "456123789");
        session.save(alaa);
        session.save(ahed);
        session.save(lana);
        session.save(ebraheem);
        session.save(zinab);
        session.save(adan);
        session.flush();

        // Add teachers
        Teacher shir = new Teacher("shir", "shirpass", "Shir", "Sneh");
        Teacher malki = new Teacher("malki", "malkipass", "Malki", "Grosman");
        Teacher dan = new Teacher("dan", "danpass", "Dan", "Rosenbaum");
        Teacher or = new Teacher("or", "orpass", "Or", "Meir");
        Teacher hagit = new Teacher("hagit", "hagitpass", "Hagit", "Hel-Or");
        session.save(shir);
        session.save(malki);
        session.save(dan);
        session.save(or);
        session.save(hagit);
        session.flush();

        // Add subjects
        Subject math = new Subject("Mathematics");
        Subject cs = new Subject("Computer Science");
        session.save(math);
        session.save(cs);
        session.flush();

        // Add courses
        Course algebra = new Course("Linear Algebra", math);
        Course calculus = new Course("Calculus", math);
        session.save(algebra);
        session.save(calculus);
        session.flush();

        math.setCourseList(new ArrayList<>(List.of(new Course[]{algebra, calculus})));
        session.save(math);
        session.flush();

        Course intro = new Course("Into to CS", cs);
        Course algo = new Course("Algorithms", cs);
        Course graphics = new Course("Computer Graphics", cs);
        Course cv = new Course("Computer Vision", cs);
        session.save(intro);
        session.save(algo);
        session.save(graphics);
        session.save(cv);
        session.flush();

        cs.setCourseList(new ArrayList<>(List.of(new Course[]{intro, algo, graphics, cv})));
        session.save(cs);
        session.flush();

        // Connect teachers to courses
        intro.setTeacherList(new ArrayList<>(List.of(new Teacher[]{hagit, dan, shir})));
        algo.setTeacherList(new ArrayList<>(List.of(new Teacher[]{malki, dan})));
        graphics.setTeacherList(new ArrayList<>(List.of(new Teacher[]{shir})));
        cv.setTeacherList(new ArrayList<>(List.of(new Teacher[]{hagit, or, malki})));
        session.save(intro);
        session.save(algo);
        session.save(graphics);
        session.save(cv);
        session.flush();

        algebra.setTeacherList(new ArrayList<>(List.of(new Teacher[]{or, dan})));
        calculus.setTeacherList(new ArrayList<>(List.of(new Teacher[]{or})));
        session.save(algebra);
        session.save(calculus);
        session.flush();

        shir.setCourseList(new ArrayList<>(List.of(new Course[]{intro, graphics})));
        malki.setCourseList(new ArrayList<>(List.of(new Course[]{algo, cv})));
        dan.setCourseList(new ArrayList<>(List.of(new Course[]{intro, algo, algebra})));
        or.setCourseList(new ArrayList<>(List.of(new Course[]{algebra, calculus, cv})));
        hagit.setCourseList(new ArrayList<>(List.of(new Course[]{intro, cv})));
        session.save(shir);
        session.save(malki);
        session.save(dan);
        session.save(or);
        session.save(hagit);
        session.flush();

        // Add questions
        Question mathQ1 = new Question("What is 5+2 ?", new ArrayList<>(List.of(new Course[]{algebra, calculus})),
                math, 0, (new String[]{"7", "3", "12", "0"}));

        Question mathQ2 = new Question("What is 5-3 ?", new ArrayList<>(List.of(new Course[]{algebra})),
                math, 2, (new String[]{"4", "1", "2", "0"}));

        Question mathQ3 = new Question("What is integral of x ?", new ArrayList<>(List.of(new Course[]{calculus})),
                math, 1, (new String[]{"x", "x^2 / 2", "2x", "Doesn't have integral!"}));

        session.save(mathQ1);
        session.save(mathQ2);
        session.save(mathQ3);
        session.flush();

        Question csQ1 = new Question("What does this print: cout << \"Hi\" << endl; ?",
                new ArrayList<>(List.of(new Course[]{intro})), cs, 0, (new String[]{"Hi", "Error!", "Null", "Hi!"}));

        Question csQ2 = new Question("Who created FFT ?", new ArrayList<>(List.of(new Course[]{graphics, cv, algo})),
                cs, 3, (new String[]{"Dr. Shuly", "Lagrange", "ME!", "Fourier"}));

        Question csQ3 = new Question("How many image pyramids do we know ?",
                new ArrayList<>(List.of(new Course[]{cv})), cs, 1, (new String[]{"1", "2", "3", "None"}));

        Question csQ4 = new Question("How do we find shortest path in graph ?",
                new ArrayList<>(List.of(new Course[]{intro, algo})), cs, 2, (new String[]{"DFS", "BFS", "Daijkstra", "A + B"}));

        Question csQ5 = new Question("How do we get edges of an image ?", new ArrayList<>(List.of(new Course[]{cv, graphics})),
                cs, 3, (new String[]{"Sobel", "Canny", "No way!", "A + B"}));

        Question csQ6 = new Question("What is recursion ?", new ArrayList<>(List.of(new Course[]{intro, algo})),
                cs, 1, (new String[]{"What is recursion ?", "Yes", "No", "Error"}));

        session.save(csQ1);
        session.save(csQ2);
        session.save(csQ3);
        session.save(csQ4);
        session.save(csQ5);
        session.save(csQ6);
        session.flush();

        // Connect questions with subjects and courses
        math.setQuestionList(new ArrayList<>(List.of(new Question[]{mathQ1, mathQ2, mathQ3})));
        session.save(math);
        session.flush();

        algebra.setQuestionList(new ArrayList<>(List.of(new Question[]{mathQ1, mathQ2})));
        calculus.setQuestionList(new ArrayList<>(List.of(new Question[]{mathQ1, mathQ3})));
        session.save(algebra);
        session.save(calculus);
        session.flush();

        cs.setQuestionList(new ArrayList<>(List.of(new Question[]{csQ1, csQ2, csQ3, csQ4, csQ5, csQ6})));
        session.save(cs);
        session.flush();

        intro.setQuestionList(new ArrayList<>(List.of(new Question[]{csQ1, csQ4, csQ6})));
        algo.setQuestionList(new ArrayList<>(List.of(new Question[]{csQ2, csQ4, csQ6})));
        cv.setQuestionList(new ArrayList<>(List.of(new Question[]{csQ2, csQ3, csQ5})));
        graphics.setQuestionList(new ArrayList<>(List.of(new Question[]{csQ2, csQ5})));
        session.save(intro);
        session.save(algo);
        session.save(graphics);
        session.save(cv);
        session.flush();

        // Add exams
        List<Question> algebraQuestions = algebra.getQuestionList();
        List<Integer> points = new ArrayList<>(List.of(new Integer[]{70, 30}));
        Exam algebraExam = new Exam("Algebra Exam moed a", 1, 60, or, "No comment!", "No    !", algebra, algebraQuestions, points);
        or.getCreatedExams().add(algebraExam);
        algebra.getExamList().add(algebraExam);
        session.save(algebraExam);
        session.save(or);
        session.save(algebra);
        for(Question q : algebraQuestions){
            q.getExamList().add(algebraExam);
            session.save(q);
        }
        session.flush();

        Exam algebraExamB = new Exam("Algebra Exam moed b", 1, 60, or, "No comment!", "No Comment!", algebra, algebraQuestions, points);
        or.getCreatedExams().add(algebraExamB);
        algebra.getExamList().add(algebraExamB);
        session.save(algebraExamB);
        session.save(or);
        session.save(algebra);
        for(Question q : algebraQuestions){
            q.getExamList().add(algebraExamB);
            session.save(q);
        }
        session.flush();

        // Add executables
        ExecutableExam executableAlgebra = new ExecutableExam("1000", algebraExam, or);
        or.getExamList().add(executableAlgebra);
        session.save(executableAlgebra);
        session.save(or);
        session.flush();

        ExecutableExam executableAlgebraB = new ExecutableExam("1001", algebraExamB, dan);
        dan.getExamList().add(executableAlgebraB);
        session.save(executableAlgebraB);
        session.save(dan);
        session.flush();

        // Add results
        HashMap<Question, Integer> answers = new HashMap<>();
        answers.put(executableAlgebra.getExam().getQuestionList().get(0), 0);
        answers.put(executableAlgebra.getExam().getQuestionList().get(1), 1);

        Result result1 = new Result(96, lana, "", executableAlgebra, 45, false, answers);
        lana.getExamList().add(executableAlgebra);
        lana.getResultList().add(result1);
        executableAlgebra.getStudentList().add(lana);
        session.save(result1);
        session.save(lana);
        session.save(executableAlgebra);
        session.flush();

        Result result2 = new Result(84, alaa, "", executableAlgebra,50, false, answers);
        alaa.getExamList().add(executableAlgebra);
        alaa.getResultList().add(result2);
        executableAlgebra.getStudentList().add(alaa);
        session.save(result2);
        session.save(alaa);
        session.save(executableAlgebra);
        session.flush();

        Result result3 = new Result(81, ahed, "", executableAlgebra,60, true, answers);
        ahed.getExamList().add(executableAlgebra);
        ahed.getResultList().add(result3);
        executableAlgebra.getStudentList().add(ahed);
        session.save(result3);
        session.save(ahed);
        session.save(executableAlgebra);
        session.flush();

        int[] distribution = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 2, 1};
        executableAlgebra.setDistribution(distribution);

        double avg = (96 + 84 + 81) / 3;
        executableAlgebra.setAverage(avg);

        executableAlgebra.setMedian(84);

        Result result4 = new Result(91, ebraheem, "", executableAlgebraB, 45, false, answers);
        ebraheem.getExamList().add(executableAlgebraB);
        ebraheem.getResultList().add(result4);
        executableAlgebraB.getStudentList().add(ebraheem);
        session.save(result4);
        session.save(ebraheem);
        session.save(executableAlgebraB);
        session.flush();

        distribution = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        executableAlgebraB.setDistribution(distribution);
        executableAlgebraB.setMedian(91);
        executableAlgebraB.setAverage(91);
        session.save(executableAlgebraB);
        session.flush();

        session.getTransaction().commit();
    }
}

